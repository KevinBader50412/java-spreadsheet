package spreadsheet.model;

/**
 * A cell position. Columns and rows are 0-based internally; "A1" is (0, 0)
 * and "B12" is (1, 11).
 */
public record CellAddress(int col, int row) {

    public CellAddress {
        if (col < 0 || row < 0) throw new IllegalArgumentException("negative cell position");
    }

    /** Parses "B12" (case-insensitive). Throws IllegalArgumentException if invalid. */
    public static CellAddress parse(String text) {
        String s = text.trim().toUpperCase();
        int i = 0;
        int col = 0;
        while (i < s.length() && Character.isLetter(s.charAt(i))) {
            col = col * 26 + (s.charAt(i) - 'A' + 1);
            i++;
        }
        if (i == 0 || i == s.length()) throw new IllegalArgumentException("not a cell address: " + text);
        int row;
        try {
            row = Integer.parseInt(s.substring(i));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a cell address: " + text);
        }
        if (row < 1) throw new IllegalArgumentException("not a cell address: " + text);
        return new CellAddress(col - 1, row - 1);
    }

    /** Column letters for a 0-based index: 0 → A, 25 → Z, 26 → AA. */
    public static String columnName(int col) {
        StringBuilder sb = new StringBuilder();
        int n = col + 1;
        while (n > 0) {
            int r = (n - 1) % 26;
            sb.insert(0, (char) ('A' + r));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return columnName(col) + (row + 1);
    }
}
