package com.dutils.servicebusviewer;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.dutils.servicebusviewer.codearea.JsonEditor;
import com.dutils.servicebusviewer.codearea.XMLEditor;
import com.dutils.servicebusviewer.config.ApplicationContext;
import com.dutils.servicebusviewer.model.DataTreeItem;
import com.dutils.servicebusviewer.model.NodeType;
import com.dutils.servicebusviewer.model.PropsTreeTableItem;
import com.dutils.servicebusviewer.model.ServiceBusMessageData;
import com.dutils.servicebusviewer.tableview.EditCell;
import com.dutils.servicebusviewer.tableview.GenericTableHelper;
import com.dutils.servicebusviewer.utils.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTreeTableCell;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.StringConverter;
import javafx.util.converter.DefaultStringConverter;
import org.apache.commons.lang3.StringUtils;
import org.fxmisc.richtext.CodeArea;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.dutils.servicebusviewer.utils.DialogUtils.*;
import static com.dutils.servicebusviewer.utils.LogUtils.log;
import static com.dutils.servicebusviewer.utils.MessageUtils.extractBody;

public class MessageDialogController {

    @FXML
    public TreeTableView<PropsTreeTableItem> propsTreeTableView;
    @FXML
    public TreeTableColumn<PropsTreeTableItem, String> propColumn;
    @FXML
    public TreeTableColumn<PropsTreeTableItem, String> valueColumn;
    @FXML
    public TableView<PropsTreeTableItem> customPropsTableView;
    @FXML
    public TableColumn<PropsTreeTableItem, String> customPropColumn;
    @FXML
    public TableColumn<PropsTreeTableItem, String> customValueColumn;
    public ButtonType submitButtonType;
    public ButtonType formatButtonType;
    public StackPane stackPane;
    public Label statusText;
    public SplitPane innerSplitPane;
    public ButtonType loadMessageButtonType;
    public ButtonType saveButtonType;
    public ButtonType nextButtonType;
    public ButtonType previousButtonType;

    @FXML
    CodeArea messageEditor;
    @FXML
    ComboBox<String> contentType;
    @FXML
    SplitPane splitPane;
    ServiceBusReceivedMessage serviceBusReceivedMessage;
    @FXML
    private DialogPane dialogPane;
    private String entityName;
    private TreeItem<DataTreeItem> selectedNode;

    private final PauseTransition highlightDelay = new PauseTransition(Duration.millis(250));

    @FXML
    public void initialize() {
        this.entityName = ApplicationContext.getInstance().getMainUIController().getEntityName();
        this.selectedNode = ApplicationContext.getInstance().getMainUIController().getSelectedNode();
        stackPane.prefHeightProperty().bind(dialogPane.heightProperty().multiply(0.85));
        Platform.runLater(() -> {
            Window window = dialogPane.getScene().getWindow();
            if (window != null) {
                window.setWidth(dialogPane.getPrefWidth());
                window.setHeight(dialogPane.getPrefHeight());
            }
        });
        innerSplitPane.setDividerPositions(0);
        propsTreeTableView.setVisible(false);
        propsTreeTableView.setManaged(false);
        intializePropsTableView();
        customPropsTableDataSetup(null);
        messageEditor.textProperty().addListener((obs, oldText, newText) -> {
            highlightDelay.stop();
            highlightDelay.setOnFinished(e -> highlightEditor(newText));
            highlightDelay.playFromStart();
        });

        dialogPane.lookupButton(previousButtonType).addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            handlePrevious(e);
        });
        dialogPane.lookupButton(nextButtonType).addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            handleNext(e);
        });

        dialogPane.lookupButton(saveButtonType).addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            handleSave(e);
        });

        dialogPane.lookupButton(submitButtonType).addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            handleResend(e);
        });
        dialogPane.lookupButton(formatButtonType).addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            handleFormat(e);
        });
        dialogPane.lookupButton(loadMessageButtonType).addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            handleLoadFile(e);
        });
        Utils.applyStyle(messageEditor, ApplicationContext.getInstance().getStyle());
        GenericTableHelper<PropsTreeTableItem> helper =
                new GenericTableHelper<>(
                        customPropsTableView,
                        () -> new PropsTreeTableItem("", "", null, true, true)
                );
//
    }

    private void handlePrevious(ActionEvent e) {
        var mainUIController = ApplicationContext.getInstance().getMainUIController();
        var tableView = mainUIController.isDlq() ? mainUIController.msgDlqTableview : mainUIController.msgTableview;
        tableSelect(tableView, -1);
    }

    private void handleNext(ActionEvent e) {
        var mainUIController = ApplicationContext.getInstance().getMainUIController();
        var tableView = mainUIController.isDlq() ? mainUIController.msgDlqTableview : mainUIController.msgTableview;
        tableSelect(tableView, 1);
    }

    void tableSelect(TableView<ServiceBusReceivedMessage> tableView, int step) {
        var model = tableView.getSelectionModel();
        var items = tableView.getItems();
        int size = items.size();
        if (size == 0) return;

        int currentIndex = model.getSelectedIndex();
        boolean atLast = currentIndex == size - 1;
        boolean atFirst = currentIndex == 0;

        startProcees("Loading Messages...");

        if (atFirst && step < 0) {
            showInfo("Message", null, "You’re already at the first message — can’t go any further up!");
            endProcess("Showing Message in Row: 1");
            return;
        }

        if (atLast && step > 0) {
            syncPeekMessages();
        }

        Platform.runLater(() -> {
            var updatedItems = tableView.getItems();
            int newSize = updatedItems.size();

            int nextIndex = currentIndex + step;
            if (nextIndex < 0) nextIndex = 0;
            else if (nextIndex >= newSize) nextIndex = newSize - 1;

            boolean reachedEnd = (nextIndex == newSize - 1 && step > 0 && atLast && newSize == size);
            if (reachedEnd) {
                showInfo("Message", null, "You’re already at the last message — nothing more to show.");
                endProcess("Showing Message in Row: " + (nextIndex + 1));
                return;
            }

            try {
                model.clearSelection();
                model.select(nextIndex);
                tableView.scrollTo(nextIndex);
                init(model.getSelectedItem());
            } catch (Exception e) {
                LogUtils.log("Exception Occurred: %s", e.getMessage());
            } finally {
                endProcess("Showing Message in Row: " + (nextIndex + 1));
            }
        });
    }

    void syncPeekMessages() {
        var mgr = ApplicationContext.getInstance().getMainUIController().messageTableViewManager;
        mgr.peekMessages(Constants.PEEK_SIZE);
        try {
            mgr.peekFuture.get();
        } catch (Exception e) {
            log("Exception Occurred: %s", e.getMessage());
        }
    }

    private void handleSave(ActionEvent e) {
        File selectedDir = fileChooser("Select Folder to Save File", e);
        if (selectedDir == null) return;
        startProcees("Saving...");
        try {
            var fileNames = MessageUtils.saveMessages(selectedDir, List.of(this.serviceBusReceivedMessage));
            endProcess("Saved Successfully. File:" + fileNames);
        } catch (IOException ex) {
            LogUtils.log("Exception Occurred: %s", ex.getMessage());
            endProcess("Save Failed." + ex.getMessage());
        }
    }

    private void handleLoadFile(ActionEvent e) {
        Stage stage = (Stage) ((Node) e.getSource()).getScene().getWindow();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("JSON Files", "*.json"),
                new FileChooser.ExtensionFilter("XML Files", "*.xml"),
                new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile == null) {
            return; // user canceled
        }
        var jsonReadResult = JsonFileUtil.readFromFile(selectedFile, ServiceBusMessageData.class);
        if (jsonReadResult.object() == null) {
            messageEditor.replaceText(jsonReadResult.rawData());
        } else {
            ServiceBusMessageData messageData = jsonReadResult.object();
            messageEditor.replaceText(messageData.getBody());
            propsTreeTableView.setVisible(true);
            propsTreeTableView.setManaged(true);
            populateTree(MapperUtils.toMap(messageData));
            customPropsTableDataSetup(messageData.getApplicationProperties());
        }
    }

    public void init(ServiceBusReceivedMessage serviceBusReceivedMessage) {
        try {
            innerSplitPane.setDividerPositions(0.7);
            propsTreeTableView.setVisible(true);
            propsTreeTableView.setManaged(true);
            this.serviceBusReceivedMessage = serviceBusReceivedMessage;
            String body = extractBody(serviceBusReceivedMessage);
            contentType.getSelectionModel().select(Utils.detectType(body));
            messageEditor.replaceText(body);
            populateTree(MapperUtils.toMap(serviceBusReceivedMessage));
            customPropsTableDataSetup(serviceBusReceivedMessage.getApplicationProperties());
        } catch (IOException e) {
            log("Exception Occured: %s", e.getMessage());
        }
    }

    public void handleContentTypeChange(ActionEvent actionEvent) {
        highlightEditor(messageEditor.getText());
    }

    void highlightEditor(String text) {
        String type = contentType.getSelectionModel().getSelectedItem();
        if (type == null) return;
        CompletableFuture
                .supplyAsync(() -> switch (type) {
                    case "JSON" -> JsonEditor.highlight(text);
                    case "XML" -> XMLEditor.highlight(text);
                    default -> null;
                })
                .thenAccept(spans -> Platform.runLater(() -> {
                    if (spans != null) messageEditor.setStyleSpans(0, spans);
                    else messageEditor.clearStyle(0, messageEditor.getLength());
                }));
    }


    private void intializePropsTableView() {
        propColumn.setCellValueFactory(param -> param.getValue().getValue().keyProperty());
        propColumn.setCellFactory(col ->
                new ConditionalEditableTreeTableCell<PropsTreeTableItem, String>(
                        new DefaultStringConverter(),
                        PropsTreeTableItem::isEditableKey
                )
        );
        propColumn.setOnEditCommit(event -> {
            PropsTreeTableItem node = event.getRowValue().getValue();
            if (node != null && node.isEditableKey()) {
                node.setKey(event.getNewValue());
            }
        });

        valueColumn.setCellValueFactory(param -> param.getValue().getValue().valueProperty());
        valueColumn.setCellFactory(col ->
                new ConditionalEditableTreeTableCell<PropsTreeTableItem, String>(
                        new DefaultStringConverter(),
                        PropsTreeTableItem::isEditableValue
                )
        );

        valueColumn.setOnEditCommit(event -> {
            PropsTreeTableItem node = event.getRowValue().getValue();
            if (node != null && node.isEditableValue()) {
                node.setValue(event.getNewValue());
            }
        });

        propColumn.setEditable(true);
        valueColumn.setEditable(true);
        propsTreeTableView.setEditable(true);
        propsTreeTableView.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        propsTreeTableView.setEditable(true);

        customPropsTableView.setEditable(true);
        customPropsTableView.getSelectionModel().setCellSelectionEnabled(true);
        customPropsTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        customPropColumn.setCellValueFactory(param -> param.getValue().keyProperty());
        customPropColumn.setOnEditCommit(event -> event.getRowValue().setKey(event.getNewValue()));
        customPropColumn.setCellFactory(EditCell.forTableColumn());

        customValueColumn.setCellFactory(EditCell.forTableColumn());
        customValueColumn.setCellValueFactory(param -> param.getValue().valueProperty());
        customValueColumn.setOnEditCommit(event -> event.getRowValue().setValue(event.getNewValue()));

//        customPropsTableView.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
//            if (event.getCode() == KeyCode.TAB && !event.isShiftDown()) {
//                event.consume();
//                var focusModel = customPropsTableView.getFocusModel();
//                var columns = customPropsTableView.getColumns();
//                TablePosition<?, ?> pos = focusModel.getFocusedCell();
//                if (pos != null) {
//                    int row = pos.getRow();
//                    int col = pos.getColumn();
//                    if (col < columns.size() - 1) {
//                        focusModel.focus(row, columns.get(col + 1));
//                        customPropsTableView.edit(row, columns.get(col + 1));
//                    } else {
//                        if (row >= customPropsTableView.getItems().size() - 1) {
//                            var newItem = new PropsTreeTableItem("", "", "", true, true);
//                            customPropsTableView.getItems().add(newItem);
//                        }
//                        focusModel.focus(row + 1, columns.getFirst());
//                        customPropsTableView.edit(row + 1, columns.getFirst());
//                    }
//
//                }
//            }
//        });

    }


    private void populateTree(Map<String, Object> map) {
        TreeItem<PropsTreeTableItem> root = addItem(null, "", "Message Properties", "", false, false);
        root.setExpanded(true);

        TreeItem<PropsTreeTableItem> core = addItem(root, "", "Core identifiers", "", false, false);
        addTreeTableItem(core, map, "messageId", "Message Id");
        addTreeTableItem(core, map, "correlationId", "Correlation Id");
        addTreeTableItem(core, map, "sessionId", "Session Id");
        addTreeTableItem(core, map, "replyToSessionId", "Reply To Session Id");
        addTreeTableItem(core, map, "partitionKey", "Partition Key");
        addTreeTableItem(core, map, "deliveryCount", "Delivery Count");
        addTreeTableItem(core, map, "sequenceNumber", "Sequence Number");
        addTreeTableItem(core, map, "enqueuedSequenceNumber", "Enqueued Sequence Number");

        TreeItem<PropsTreeTableItem> routing = addItem(root, "", "Routing", "", false, false);
        addTreeTableItem(routing, map, "to", "To");
        addTreeTableItem(routing, map, "replyTo", "Reply To");
        addTreeTableItem(routing, map, "subject", "Subject");
        addTreeTableItem(routing, map, "contentType", "Content Type");

        TreeItem<PropsTreeTableItem> timing = addItem(root, "", "State & timing", "", false, false);
        addTreeTableItem(timing, map, "state", "State");
        addTreeTableItem(timing, map, "scheduledEnqueueTime", "Scheduled Enqueue Time");
        addTreeTableItem(timing, map, "enqueuedTime", "Enqueued Time");
        addTreeTableItem(timing, map, "lockedUntil", "Locked Until");
        addTreeTableItem(timing, map, "expiresAt", "Expires At");
        addTreeTableItem(timing, map, "timeToLive", "Time To Live");

        TreeItem<PropsTreeTableItem> dl = addItem(root, "", "Dead-letter", "", false, false);
        addTreeTableItem(dl, map, "deadLetterReason", "Dead Letter Reason");
        addTreeTableItem(dl, map, "deadLetterErrorDescription", "Dead Letter Error Description");
        addTreeTableItem(dl, map, "deadLetterSource", "Dead Letter Source");

        propsTreeTableView.setRoot(root);
        propsTreeTableView.setShowRoot(false);
    }

    private void addTreeTableItem(TreeItem<PropsTreeTableItem> parent, Map<String, Object> map, String key, String label) {
        Object val = map.getOrDefault(key, "");
        addItem(parent, key, label, val, false, true);
    }

    TreeItem<PropsTreeTableItem> addItem(TreeItem<PropsTreeTableItem> parent, String fieldName, String key, Object value, boolean editableKey, boolean editableValue) {
        var strValue = value == null ? "" : value.toString();
        var itemValue = new PropsTreeTableItem(fieldName, key, strValue, editableKey, editableValue);
        var item = new TreeItem<>(itemValue);
        if (parent != null) parent.getChildren().add(item);
        item.setExpanded(true);
        return item;
    }

    private void customPropsTableDataSetup(Map<String, Object> properties) {
        ObservableList<PropsTreeTableItem> items = FXCollections.observableArrayList();
        if (properties != null) {
            properties.forEach((k, v) -> items.add(new PropsTreeTableItem(k, k, v, true, true)));
        }
        items.add(new PropsTreeTableItem("", "", "", true, true));
        customPropsTableView.setItems(items);
    }

    public void handleFormat(ActionEvent actionEvent) {
        try {
            String selectedItem = contentType.getSelectionModel().getSelectedItem();
            switch (selectedItem) {
                case "JSON" -> setupFormatedText(JsonEditor::format);
                case "XML" -> setupFormatedText(XMLEditor::format);
                default -> {
                    var body = extractBody(serviceBusReceivedMessage);
                    if (body == null) return;
                    messageEditor.replaceText(body);
                }
            }
        } catch (IOException e) {
            LogUtils.log("Exception Occured: %s", e.getMessage());
        }
    }

    void setupFormatedText(Function<String, String> formatter) {
        var text = messageEditor.getText();
        try {
            String formatted = formatter.apply(text);
            if (!formatted.equals(text)) {
                messageEditor.replaceText(formatted);
            }
        } catch (Exception e) {
            LogUtils.log("Exception Occurred: %s", e.getMessage());
            return;
        }
    }

    public void handleResend(ActionEvent actionEvent) {
        int processSeq = processCount.get() + 1;
        var item = selectedNode.getValue();
        boolean isSubs = item.getType() == NodeType.SUBSCRIPTION;
        var type = item.getType();
        var entity = isSubs ? selectedNode.getParent().getValue().getName() : entityName;

        startProcees("Submitting Message:" + processSeq);
        Thread.ofVirtual().start(() -> {
            try {
                ServiceBusMessageData messageData = createServiceBusMessageData();
                Map<String, Object> props = customPropsTableView.getItems().stream()
                        .filter(d -> StringUtils.isNotBlank(d.getKey()))
                        .collect(Collectors.toMap(
                                d -> d.getKey().trim(),
                                PropsTreeTableItem::getValue,
                                (v1, v2) -> v2));

                messageData.getApplicationProperties().putAll(props);
                MessageUtils.resubmitNewMessage(type, entity, messageData);
                var str = String.format("Message Submitted(%s).", processSeq);
                Platform.runLater(() -> endProcess(str));
            } catch (Exception e) {
                Thread.currentThread().interrupt();
                var str = String.format("Interrupted.Message Resubmission Failed. %s", e.getMessage());
                Platform.runLater(() -> endProcess(str));
            }
        });
    }

    private ServiceBusMessageData createServiceBusMessageData() {
        var ret = new ServiceBusMessageData();
        ret.setBody(messageEditor.getText());

        var root = propsTreeTableView.getRoot();
        if (root == null) {
            return ret;
        }
        var map = buildFieldNameMap(root);
        var temp = new PropsTreeTableItem(null, "", null, true, true);
        ret.setMessageId(map.getOrDefault("messageId", temp).getValue());
        ret.setCorrelationId(map.getOrDefault("correlationId", temp).getValue());
        ret.setTo(map.getOrDefault("to", temp).getValue());
        ret.setReplyTo(map.getOrDefault("replyTo", temp).getValue());
        ret.setSubject(map.getOrDefault("subject", temp).getValue());
        ret.setContentType(map.getOrDefault("contentType", temp).getValue());
        ret.setReplyToSessionId(map.getOrDefault("replyToSessionId", temp).getValue());
        ret.setSessionId(map.getOrDefault("sessionId", temp).getValue());
        ret.setPartitionKey(map.getOrDefault("partitionKey", temp).getValue());

        ret.setScheduledEnqueueTime(
                Utils.parseOffsetDateTime(map.getOrDefault("scheduledEnqueueTime", temp).getValue())
        );

        // Optional future fields
        // ret.setTimeToLive(Utils.parseDuration(map.getOrDefault("timeToLive", new PropsTreeTableItem()).getValue()));

        return ret;
    }

    private Map<String, PropsTreeTableItem> buildFieldNameMap(TreeItem<PropsTreeTableItem> root) {
        Map<String, PropsTreeTableItem> map = new HashMap<>();
        collectItemsToMap(root, map);
        return map;
    }

    private void collectItemsToMap(TreeItem<PropsTreeTableItem> node, Map<String, PropsTreeTableItem> map) {
        if (node == null) return;

        PropsTreeTableItem item = node.getValue();
        if (item != null) {
            String fieldName = item.getFieldName();
            if (fieldName != null && !fieldName.isBlank()) {
                map.put(fieldName, item);
            }
        }

        for (TreeItem<PropsTreeTableItem> child : node.getChildren()) {
            collectItemsToMap(child, map);
        }
    }

    AtomicInteger processCount = new AtomicInteger(0);

    public void startProcees(String message) {
        processCount.incrementAndGet();
        statusText.setText(message);
        splitPane.setDisable(true);
    }

    private void endProcess(String message) {
        int count = processCount.decrementAndGet();
        if (count <= 0) {
            processCount.set(0);
            statusText.setText(message);
            splitPane.setDisable(false);
        }
    }
}

class ConditionalEditableTreeTableCell<S, T> extends TextFieldTreeTableCell<S, T> {
    private final Predicate<S> editableCondition;

    public ConditionalEditableTreeTableCell(StringConverter<T> converter, Predicate<S> editableCondition) {
        super(converter);
        this.editableCondition = editableCondition;
    }

    @Override
    public void startEdit() {
        var row = getTableRow();
        S item = row != null ? row.getItem() : null;
        if (item != null && editableCondition.test(item)) {
            super.startEdit();
        }
    }

    @Override
    public void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        var row = getTableRow();
        S node = (row == null) ? null : row.getItem();
        setEditable(node != null && editableCondition.test(node));
    }
}
