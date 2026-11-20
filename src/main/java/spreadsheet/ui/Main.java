package spreadsheet.ui;

/**
 * Plain entry point for running from the classpath ({@code java -cp ... spreadsheet.ui.Main}).
 * JavaFX refuses to start when the main class itself extends Application and
 * JavaFX is not on the module path, so this class just delegates.
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        SpreadsheetApp.main(args);
    }
}
