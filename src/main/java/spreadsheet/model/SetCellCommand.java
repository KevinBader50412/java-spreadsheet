package spreadsheet.model;

/** One edit, remembered so it can be undone and redone. */
public record SetCellCommand(CellAddress address, String oldRaw, String newRaw) {

    void apply(Spreadsheet sheet) {
        sheet.setCell(address, newRaw);
    }

    void revert(Spreadsheet sheet) {
        sheet.setCell(address, oldRaw);  // the normal edit path keeps dependencies correct
    }
}
