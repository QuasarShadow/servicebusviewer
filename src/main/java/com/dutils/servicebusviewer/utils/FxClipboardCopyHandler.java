package com.dutils.servicebusviewer.utils;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public final class FxClipboardCopyHandler {

    private FxClipboardCopyHandler() {
    }


    public static void enableCopy(Node control) {
        control.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if ((event.isControlDown() || event.isMetaDown()) && event.getCode() == KeyCode.C) {
                String text = null;

                if (control instanceof TableView<?> table) {
                    text = copySelectedCells(table);
                } else if (control instanceof TreeView<?> tree) {
                    text = copySelectedTreeItem(tree);
                }

                if (text != null && !text.isBlank()) {
                    ClipboardContent content = new ClipboardContent();
                    content.putString(text);
                    Clipboard.getSystemClipboard().setContent(content);
                }

                event.consume();
            }
        });
    }

    private static String copySelectedCells(TableView<?> table) {
        var selectedCells = table.getSelectionModel().getSelectedCells();
        if (selectedCells.isEmpty()) return null;

        var sb = new StringBuilder();
        int lastRow = -1;

        for (TablePosition<?, ?> position : selectedCells) {
            int row = position.getRow();
            Object cellData = position.getTableColumn().getCellData(row);

            if (lastRow == row) {
                sb.append("\t"); // same row → tab-separated
            } else if (lastRow != -1) {
                sb.append("\n"); // new row → newline
            }

            sb.append(cellData != null ? cellData.toString() : "");
            lastRow = row;
        }
        return sb.toString();
    }

    private static String copySelectedTreeItem(TreeView<?> tree) {
        TreeItem<?> selected = tree.getSelectionModel().getSelectedItem();
        return (selected != null && selected.getValue() != null)
                ? selected.getValue().toString()
                : null;
    }
}

