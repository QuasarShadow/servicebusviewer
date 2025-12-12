package com.dutils.servicebusviewer.tableview;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;

import java.util.function.Supplier;

public class GenericTableHelper<T> {

    private final TableView<T> tableView;
    private final Supplier<T> rowFactory;
    private final ObservableList<T> clipboardBuffer = FXCollections.observableArrayList();

    public GenericTableHelper(TableView<T> tableView, Supplier<T> rowFactory) {
        this.tableView = tableView;
        this.rowFactory = rowFactory;
        installKeyHandlers();
    }

    private void installKeyHandlers() {
        tableView.setOnKeyPressed(event -> {
            if (event.isControlDown()) {
                switch (event.getCode()) {
                    case C -> copySelection();
                    case X -> cutSelection();
                    case V -> pasteSelection();
                    case N -> addRowAfterSelection();
                }
            } else {
                switch (event.getCode()) {
                    case DELETE -> deleteSelection();
                    case INSERT -> addRowAfterSelection();
                    case ENTER -> addRowIfLastSelected();
                    default -> { /* ignore */ }
                }
            }
        });
    }

    public void addRowAfterSelection() {
        int index = tableView.getSelectionModel().getSelectedIndex();
        if (index < 0) index = tableView.getItems().size() - 1;
        tableView.getItems().add(index + 1, rowFactory.get());
        tableView.getSelectionModel().select(index + 1);
    }

    private void addRowIfLastSelected() {
        int lastIndex = tableView.getItems().size() - 1;
        if (tableView.getSelectionModel().getSelectedIndex() == lastIndex) {
            addRowAfterSelection();
        }
    }

    /** Deletes currently selected rows. */
    public void deleteSelection() {
        ObservableList<T> selected = tableView.getSelectionModel().getSelectedItems();
        tableView.getItems().removeAll(selected);
    }

    /** Copies selected rows into memory clipboard (not system clipboard). */
    public void copySelection() {
        clipboardBuffer.setAll(tableView.getSelectionModel().getSelectedItems());
    }

    /** Cuts selected rows into memory clipboard. */
    public void cutSelection() {
        copySelection();
        deleteSelection();
    }

    /** Pastes clipboard rows after the current selection. */
    public void pasteSelection() {
        if (clipboardBuffer.isEmpty()) return;
        int index = tableView.getSelectionModel().getSelectedIndex();
        if (index < 0) index = tableView.getItems().size() - 1;
        tableView.getItems().addAll(index + 1, clipboardBuffer);
    }

    /** Optional helper for cell factories to make all cells editable easily. */
    public static <S> void makeEditable(TableView<S> table) {
        table.setEditable(true);
        for (TableColumn<S, ?> col : table.getColumns()) {
            if (col.getCellFactory() == null) {
                ((TableColumn<S, String>) col)
                        .setCellFactory(TextFieldTableCell.forTableColumn());
            }
        }
    }

    /** Optionally, to make selection multiple. */
    public static <S> void enableMultiSelect(TableView<S> table) {
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }
}



