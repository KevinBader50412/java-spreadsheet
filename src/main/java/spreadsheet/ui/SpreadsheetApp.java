package spreadsheet.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import spreadsheet.model.CellAddress;
import spreadsheet.model.SheetFileIO;
import spreadsheet.model.Spreadsheet;
import spreadsheet.model.UndoManager;

/**
 * JavaFX entry point: {@code mvn javafx:run}, optionally with a sheet file to open.
 * The model knows nothing about the UI; the UI listens for changed cells.
 */
public class SpreadsheetApp extends Application {

    private final Spreadsheet sheet = new Spreadsheet();
    private final UndoManager undo = new UndoManager(sheet);
    private GridView grid;
    private FormulaBar formulaBar;
    private final Label status = new Label("Ready");
    private Stage stage;
    private Path file;

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        grid = new GridView(sheet, undo::edit);
        formulaBar = new FormulaBar(text -> {
            CellAddress a = grid.selected();
            if (a != null) undo.edit(a, text);
            grid.requestFocus();
        });

        // Observer: the model reports which cells changed after every edit.
        sheet.addListener(changed -> {
            grid.refresh();
            showSelected();
            status.setText(String.format("Recalculated %d cell%s in %.2f ms", sheet.lastCellsEvaluated(),
                    sheet.lastCellsEvaluated() == 1 ? "" : "s", sheet.lastRecalcNanos() / 1e6));
        });
        grid.getSelectionModel().getSelectedCells().addListener(
                (javafx.collections.ListChangeListener<Object>) c -> showSelected());

        status.setPadding(new Insets(4, 8, 4, 8));
        BorderPane root = new BorderPane(grid);
        root.setTop(new VBox(menus(), formulaBar));
        root.setBottom(status);

        stage.setScene(new Scene(root, 1000, 650));
        updateTitle();
        stage.show();

        List<String> args = getParameters().getRaw();
        if (!args.isEmpty()) open(Path.of(args.get(0)));
        grid.select(new CellAddress(0, 0));
        grid.requestFocus();
    }

    private void showSelected() {
        CellAddress a = grid.selected();
        if (a != null) formulaBar.show(a.toString(), sheet.rawAt(a));
    }

    private MenuBar menus() {
        MenuItem newItem = item("New", "Shortcut+N", () -> {
            sheet.clear();
            undo.clear();
            file = null;
            updateTitle();
        });
        MenuItem openItem = item("Open...", "Shortcut+O", () -> {
            File f = chooser("Open sheet").showOpenDialog(stage);
            if (f != null) open(f.toPath());
        });
        MenuItem saveItem = item("Save", "Shortcut+S", () -> {
            if (file == null) saveAs();
            else save(file);
        });
        MenuItem saveAsItem = item("Save As...", null, this::saveAs);
        MenuItem exportItem = item("Export CSV...", null, () -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Export values as CSV");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
            File f = fc.showSaveDialog(stage);
            if (f != null) run(() -> SheetFileIO.exportCsv(sheet, f.toPath()));
        });
        Menu fileMenu = new Menu("File", null, newItem, openItem, saveItem, saveAsItem, new SeparatorMenuItem(), exportItem);

        MenuItem undoItem = item("Undo", "Shortcut+Z", undo::undo);
        MenuItem redoItem = item("Redo", "Shortcut+Y", undo::redo);
        Menu editMenu = new Menu("Edit", null, undoItem, redoItem);
        editMenu.setOnShowing(e -> {
            undoItem.setDisable(!undo.canUndo());
            redoItem.setDisable(!undo.canRedo());
        });
        return new MenuBar(fileMenu, editMenu);
    }

    private MenuItem item(String text, String shortcut, Runnable action) {
        MenuItem item = new MenuItem(text);
        if (shortcut != null) item.setAccelerator(KeyCombination.keyCombination(shortcut));
        item.setOnAction(e -> action.run());
        return item;
    }

    private FileChooser chooser(String title) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Sheets", "*.sheet"));
        return fc;
    }

    private void open(Path path) {
        run(() -> {
            SheetFileIO.load(sheet, path);
            undo.clear();
            file = path;
            updateTitle();
            status.setText("Opened " + path.getFileName());
        });
    }

    private void saveAs() {
        File f = chooser("Save sheet").showSaveDialog(stage);
        if (f != null) save(f.toPath());
    }

    private void save(Path path) {
        run(() -> {
            SheetFileIO.save(sheet, path);
            file = path;
            updateTitle();
            status.setText("Saved " + path.getFileName());
        });
    }

    private void updateTitle() {
        stage.setTitle("Spreadsheet - " + (file == null ? "Untitled" : file.getFileName()));
    }

    private interface IoAction {
        void run() throws IOException;
    }

    private void run(IoAction action) {
        try {
            action.run();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, e.getMessage()).showAndWait();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
