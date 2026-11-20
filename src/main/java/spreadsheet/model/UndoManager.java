package spreadsheet.model;

import java.util.ArrayDeque;

/** Undo/redo with the command pattern: every edit goes through {@link #edit}. */
public final class UndoManager {
    private final Spreadsheet sheet;
    private final ArrayDeque<SetCellCommand> undo = new ArrayDeque<>();
    private final ArrayDeque<SetCellCommand> redo = new ArrayDeque<>();

    public UndoManager(Spreadsheet sheet) {
        this.sheet = sheet;
    }

    public void edit(CellAddress address, String newRaw) {
        String oldRaw = sheet.rawAt(address);
        if (oldRaw.equals(newRaw)) return;
        SetCellCommand cmd = new SetCellCommand(address, oldRaw, newRaw);
        cmd.apply(sheet);
        undo.push(cmd);
        redo.clear();  // a new edit makes the redo history invalid
    }

    public void edit(String address, String newRaw) {
        edit(CellAddress.parse(address), newRaw);
    }

    public boolean undo() {
        if (undo.isEmpty()) return false;
        SetCellCommand cmd = undo.pop();
        cmd.revert(sheet);
        redo.push(cmd);
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) return false;
        SetCellCommand cmd = redo.pop();
        cmd.apply(sheet);
        undo.push(cmd);
        return true;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public void clear() {
        undo.clear();
        redo.clear();
    }
}
