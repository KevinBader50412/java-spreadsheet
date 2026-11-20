package spreadsheet.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Saving and opening sheets, and exporting values to CSV.
 *
 * <p>The sheet format stores what the user typed, so formulas are preserved:
 * one line per non-empty cell, {@code A1<TAB>content}, with backslash, tab and
 * newline escaped.
 */
public final class SheetFileIO {
    private static final String HEADER = "#sheet v1";

    private SheetFileIO() {}

    public static void save(Spreadsheet sheet, Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER);
        sortedCells(sheet).forEach(e -> lines.add(e.getKey() + "\t" + escape(e.getValue())));
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    /** Replaces the sheet's contents with the file's. */
    public static void load(Spreadsheet sheet, Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).equals(HEADER)) throw new IOException("not a sheet file");
        sheet.clear();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty()) continue;
            int tab = line.indexOf('\t');
            if (tab < 0) throw new IOException("line " + (i + 1) + " is malformed");
            try {
                sheet.setCell(CellAddress.parse(line.substring(0, tab)), unescape(line.substring(tab + 1)));
            } catch (IllegalArgumentException e) {
                throw new IOException("line " + (i + 1) + ": " + e.getMessage());
            }
        }
    }

    /** Writes the computed values (not formulas), up to the last used row and column. */
    public static void exportCsv(Spreadsheet sheet, Path file) throws IOException {
        int maxCol = -1, maxRow = -1;
        for (CellAddress a : sheet.nonEmptyCells().keySet()) {
            maxCol = Math.max(maxCol, a.col());
            maxRow = Math.max(maxRow, a.row());
        }
        List<String> lines = new ArrayList<>();
        for (int r = 0; r <= maxRow; r++) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c <= maxCol; c++) {
                if (c > 0) sb.append(',');
                sb.append(csvField(sheet.valueAt(new CellAddress(c, r)).display()));
            }
            lines.add(sb.toString());
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static List<Map.Entry<CellAddress, String>> sortedCells(Spreadsheet sheet) {
        return sheet.nonEmptyCells().entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<CellAddress, String> e) -> e.getKey().row())
                        .thenComparing(e -> e.getKey().col()))
                .toList();
    }

    private static String csvField(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n");
    }

    static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(n == 't' ? '\t' : n == 'n' ? '\n' : n);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
