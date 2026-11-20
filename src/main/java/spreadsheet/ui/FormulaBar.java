package spreadsheet.ui;

import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/** Shows the selected cell's address and raw content; Enter commits an edit. */
final class FormulaBar extends HBox {
    private final Label address = new Label();
    private final TextField content = new TextField();

    FormulaBar(Consumer<String> onCommit) {
        super(8);
        address.setMinWidth(50);
        address.setStyle("-fx-font-weight: bold;");
        content.setPromptText("Value or formula, e.g. =SUM(A1:A5)");
        content.setOnAction(e -> onCommit.accept(content.getText()));
        HBox.setHgrow(content, Priority.ALWAYS);
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(6, 8, 6, 8));
        getChildren().addAll(address, new Label("fx"), content);
    }

    void show(String cellAddress, String raw) {
        address.setText(cellAddress);
        content.setText(raw);
    }
}
