package spreadsheet.formula;

/** The formula text is not valid. The cell shows #PARSE!. */
public class ParseException extends Exception {
    private static final long serialVersionUID = 1L;

    public ParseException(String message) {
        super(message);
    }
}
