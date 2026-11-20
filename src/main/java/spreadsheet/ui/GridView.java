package spreadsheet.ui;

import java.util.function.BiConsumer;
import java.util.stream.IntStream;
import javafx.collections.FXCollections;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.beans.property.ReadOnlyObjectWrapper;
import spreadsheet.model.CellAddress;
import spreadsheet.model.Spreadsheet;
import spreadsheet.model.Value;

/**
 * The grid. Each table row is one sheet row (the item is the row index), with a
 * row-number column followed by one column per sheet column.
 */
final class GridView extends TableView<Integer> {

    private final Spreadsheet sheet;
    private final BiConsumer<CellAddress, String> onEdit;
    private String pendingText;  // the key that started an edit by typing

    GridView(Spreadsheet sheet, BiConsumer<CellAddress, String> onEdit) {
        this.sheet = sheet;
        this.onEdit = onEdit;
        setItems(FXCollections.observableArrayList(IntStream.range(0, sheet.rows()).boxed().toList()));
        setEditable(true);
        getSelectionModel().setCellSelectionEnabled(true);
        setColumnResizePolicy(UNCONSTRAINED_RESIZE_POLICY);

        TableColumn<Integer, Integer> header = new TableColumn<>("");
        header.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue() + 1));
        header.setSortable(false);
        header.setEditable(false);
        header.setPrefWidth(45);
        header.setStyle("-fx-alignment: CENTER; -fx-background-color: #f1f3f4;");
        getColumns().add(header);

        for (int c = 0; c < sheet.columns(); c++) {
            final int col = c;
            TableColumn<Integer, Integer> column = new TableColumn<>(CellAddress.columnName(c));
            column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
            column.setCellFactory(tc -> new SheetCell(col));
            column.setSortable(false);
            column.setPrefWidth(95);
            getColumns().add(column);
        }

        // Start editing by typing, as in any spreadsheet.
        addEventFilter(KeyEvent.KEY_TYPED, e -> {
            if (getEditingCell() != null) return;
            String ch = e.getCharacter();
            if (ch.isEmpty() || Character.isISOControl(ch.charAt(0))) return;
            CellAddress a = selected();
            if (a == null) return;
            pendingText = ch;
            edit(a.row(), getColumns().get(a.col() + 1));
            e.consume();
        });
        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (getEditingCell() != null) return;
            CellAddress a = selected();
            if (a == null) return;
            if (e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE) {
                onEdit.accept(a, "");
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.F2) {
                edit(a.row(), getColumns().get(a.col() + 1));
                e.consume();
            }
        });
    }

    /** The selected cell, or null (the row-number column does not count). */
    CellAddress selected() {
        if (getSelectionModel().getSelectedCells().isEmpty()) return null;
        TablePosition<?, ?> pos = getSelectionModel().getSelectedCells().get(0);
        int col = getColumns().indexOf(pos.getTableColumn()) - 1;
        if (col < 0 || pos.getRow() < 0) return null;
        return new CellAddress(col, pos.getRow());
    }

    void select(CellAddress a) {
        getSelectionModel().clearAndSelect(a.row(), getColumns().get(a.col() + 1));
        scrollTo(Math.max(0, a.row() - 5));
    }

    /** One grid cell: shows the computed value, edits the raw content. */
    private final class SheetCell extends TableCell<Integer, Integer> {
        private final int col;
        private TextField field;

        SheetCell(int col) {
            this.col = col;
        }

        private CellAddress address() {
            return new CellAddress(col, getIndex());
        }

        @Override
        protected void updateItem(Integer row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setText(null);
                setGraphic(null);
                setStyle("");
                return;
            }
            if (isEditing()) return;
            Value v = sheet.valueAt(address());
            setText(v.display());
            setGraphic(null);
            setStyle(switch (v) {
                case Value.ErrorValue e -> "-fx-text-fill: #c62828; -fx-alignment: CENTER-LEFT;";
                case Value.NumberValue n -> "-fx-alignment: CENTER-RIGHT;";
                default -> "-fx-alignment: CENTER-LEFT;";
            });
        }

        @Override
        public void startEdit() {
            super.startEdit();
            if (!isEditing()) return;
            field = new TextField(pendingText != null ? pendingText : sheet.rawAt(address()));
            pendingText = null;
            field.setOnAction(e -> commit(true));
            field.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) cancelEdit();
            });
            field.focusedProperty().addListener((o, was, is) -> {
                if (!is && isEditing()) commit(false);
            });
            setText(null);
            setGraphic(field);
            field.requestFocus();
            field.end();
        }

        private void commit(boolean moveDown) {
            CellAddress a = address();
            String text = field.getText();
            cancelEdit();  // leave edit mode first; the model change triggers a refresh
            onEdit.accept(a, text);
            if (moveDown && a.row() + 1 < sheet.rows()) select(new CellAddress(a.col(), a.row() + 1));
            GridView.this.requestFocus();
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            field = null;
            updateItem(getItem(), isEmpty());
        }
    }
}
