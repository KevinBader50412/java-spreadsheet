package spreadsheet.model;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpreadsheetTest {

    private final Spreadsheet sheet = new Spreadsheet();

    private String v(String address) {
        return sheet.valueAt(address).display();
    }

    @Test
    void chainRecalculates() {
        sheet.setCell("A1", "1");
        sheet.setCell("B1", "=A1+1");
        sheet.setCell("C1", "=B1+1");
        assertEquals("3", v("C1"));
        sheet.setCell("A1", "10");
        assertEquals("12", v("C1"));
    }

    @Test
    void diamondEvaluatesEachCellOnce() {
        sheet.setCell("A1", "1");
        sheet.setCell("B1", "=A1*2");
        sheet.setCell("C1", "=A1*3");
        sheet.setCell("D1", "=B1+C1");
        Set<CellAddress> changed = sheet.setCell("A1", "10");
        assertEquals(Set.of(CellAddress.parse("A1"), CellAddress.parse("B1"), CellAddress.parse("C1"),
                CellAddress.parse("D1")), changed);
        assertEquals(4, sheet.lastCellsEvaluated());  // D1 once, not once per path
        assertEquals("50", v("D1"));
    }

    @Test
    void oldEdgesAreRemoved() {
        sheet.setCell("A1", "1");
        sheet.setCell("C1", "5");
        sheet.setCell("B1", "=A1");
        sheet.setCell("B1", "=C1");
        Set<CellAddress> changed = sheet.setCell("A1", "2");
        assertEquals(Set.of(CellAddress.parse("A1")), changed);  // B1 no longer reads A1
        assertEquals("5", v("B1"));
    }

    @Test
    void cyclesAreDetectedAndBreakingThemRestoresValues() {
        sheet.setCell("A1", "=A1");
        assertEquals("#CYCLE!", v("A1"));

        sheet.setCell("B1", "=C1");
        sheet.setCell("C1", "=B1");
        assertEquals("#CYCLE!", v("B1"));
        assertEquals("#CYCLE!", v("C1"));

        sheet.setCell("D1", "=E1+1");
        sheet.setCell("E1", "=F1+1");
        sheet.setCell("F1", "=D1+1");
        sheet.setCell("G1", "=D1*2");  // reads from the cycle
        for (String c : new String[] {"D1", "E1", "F1", "G1"}) assertEquals("#CYCLE!", v(c), c);

        sheet.setCell("F1", "1");  // break the 3-cell loop
        assertEquals("1", v("F1"));
        assertEquals("2", v("E1"));
        assertEquals("3", v("D1"));
        assertEquals("6", v("G1"));

        sheet.setCell("A1", "7");
        assertEquals("7", v("A1"));
    }

    @Test
    void undoAndRedo() {
        UndoManager undo = new UndoManager(sheet);
        undo.edit("A1", "1");
        undo.edit("B1", "=A1*10");
        undo.edit("A1", "2");
        assertEquals("20", v("B1"));

        assertTrue(undo.undo());
        assertEquals("10", v("B1"));
        assertTrue(undo.undo());
        assertEquals("", v("B1"));
        assertTrue(undo.redo());
        assertEquals("10", v("B1"));

        undo.edit("A1", "5");  // a new edit clears the redo stack
        assertFalse(undo.redo());
        assertEquals("50", v("B1"));
    }

    @Test
    void saveAndLoadKeepFormulas(@TempDir Path dir) throws Exception {
        sheet.setCell("A1", "3");
        sheet.setCell("A2", "4");
        sheet.setCell("A3", "=SUM(A1:A2)");
        sheet.setCell("B1", "tab\there, \"quoted\"");
        Path file = dir.resolve("test.sheet");
        SheetFileIO.save(sheet, file);

        Spreadsheet loaded = new Spreadsheet();
        SheetFileIO.load(loaded, file);
        assertEquals("=SUM(A1:A2)", loaded.rawAt(CellAddress.parse("A3")));
        assertEquals("7", loaded.valueAt("A3").display());
        assertEquals(sheet.nonEmptyCells(), loaded.nonEmptyCells());

        Path csv = dir.resolve("out.csv");
        SheetFileIO.exportCsv(loaded, csv);
        assertEquals("3,\"tab\there, \"\"quoted\"\"\"", Files.readAllLines(csv).get(0));
        assertEquals("7,", Files.readAllLines(csv).get(2));
    }

    @Test
    void incrementalRecalculationVersusRecalculatingEverything() {
        int n = 10_000;
        Spreadsheet big = new Spreadsheet(1, n);
        big.setCell("A1", "1");
        for (int i = 2; i <= n; i++) big.setCell("A" + i, "=A" + (i - 1) + "+1");
        assertEquals(String.valueOf(n), big.valueAt("A" + n).display());

        // Warm up the JIT a little so the timings mean something.
        for (int i = 0; i < 5; i++) {
            big.setCell("A" + n, "=A" + (n - 1) + "+1");
            big.recalculateAll();
        }

        big.setCell("A" + n, "=A" + (n - 1) + "+2");  // a change near the end: 1 cell affected
        long endNs = big.lastRecalcNanos();
        int endCells = big.lastCellsEvaluated();

        big.setCell("A1", "5");  // a change at the start: the whole chain is affected
        long startNs = big.lastRecalcNanos();
        int startCells = big.lastCellsEvaluated();

        big.recalculateAll();  // naive approach: recalculate every cell
        long allNs = big.lastRecalcNanos();

        System.out.printf("10,000-cell chain: change at the end %d cell(s) %.3f ms, change at the start %d cells "
                + "%.2f ms, recalculate everything %.2f ms%n", endCells, endNs / 1e6, startCells, startNs / 1e6,
                allNs / 1e6);
        assertEquals(1, endCells);
        assertEquals(n, startCells);
        assertEquals(String.valueOf(n + 5), big.valueAt("A" + n).display());
        assertTrue(endNs < allNs);
    }
}
