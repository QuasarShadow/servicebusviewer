package com.dutils.servicebusviewer;

import atlantafx.base.theme.*;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.administration.models.EntityStatus;
import com.azure.messaging.servicebus.administration.models.QueueProperties;
import com.azure.messaging.servicebus.administration.models.SubscriptionProperties;
import com.azure.messaging.servicebus.administration.models.TopicProperties;
import com.dutils.servicebusviewer.config.ApplicationContext;
import com.dutils.servicebusviewer.mgr.MainUIMessageTableViewManager;
import com.dutils.servicebusviewer.mgr.MainUITreeAndTableViewManager;
import com.dutils.servicebusviewer.model.DataTreeItem;
import com.dutils.servicebusviewer.model.NodeType;
import com.dutils.servicebusviewer.utils.Constants;
import com.dutils.servicebusviewer.utils.FxClipboardCopyHandler;
import com.dutils.servicebusviewer.utils.LogUtils;
import com.dutils.servicebusviewer.utils.MessageUtils;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.Pair;
import org.apache.commons.lang3.time.StopWatch;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.dutils.servicebusviewer.utils.DialogUtils.*;
import static com.dutils.servicebusviewer.utils.Utils.fd;
import static com.dutils.servicebusviewer.utils.Utils.getEntityProperty;


public class MainUIController implements Initializable {
    @FXML
    public ProgressIndicator progressIndicator;
    @FXML
    public HBox progressBox;
    public Label progressLabel;
    public SplitPane topSplitPane;
    public Button btnRestoreSel;
    public Button btnRestoreAll;
    public Button btnSaveMsg;
    public MenuItem mnuStatus;
    @FXML
    private ToggleGroup styleGroup;

    @FXML
    public TextArea logNode;
    @FXML
    public ScrollPane logScrollPane;
    @FXML
    public TableView<ServiceBusReceivedMessage> msgTableview;
    @FXML
    public Button btnpeek;

    @FXML
    public TableView<Pair<String, String>> tableview;
    public MenuItem mnuRefresh;
    public MenuItem mnuSendMsg;
    public MenuItem mnuPurge;
    public MenuItem mnuDlq;
    public MenuItem mnuSortByMessage;
    public MenuItem mnuSortByName;
    public TableView<ServiceBusReceivedMessage> msgDlqTableview;
    public TabPane messageViewTabPane;
    public MenuItem mnuSendToDlq;

    @FXML
    private TreeView<DataTreeItem> treeview;
    @FXML
    public Label statusText;
    ApplicationContext context = ApplicationContext.getInstance();
    MainUITreeAndTableViewManager treeAndTableViewManager;
    MainUIMessageTableViewManager messageTableViewManager;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        context.setMainUIController(this);
        treeAndTableViewManager = new MainUITreeAndTableViewManager(treeview, tableview);
        messageTableViewManager = new MainUIMessageTableViewManager(msgTableview, msgDlqTableview);
        FxClipboardCopyHandler.enableCopy(treeview);
        FxClipboardCopyHandler.enableCopy(tableview);
        btnRestoreAll.setVisible(false);
        btnRestoreSel.setVisible(false);
        btnRestoreAll.setManaged(false);
        btnRestoreSel.setManaged(false);
        treeview.getSelectionModel().selectedItemProperty().addListener(this::treeNodeSelected);
        messageViewTabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            treeNodeSelected(null, null, treeview.getSelectionModel().getSelectedItem());
            if (newValue != null) {
                String userData = (String) newValue.getUserData();
                boolean dlqmessages = userData.equals("DLQMESSAGES");
                btnRestoreAll.setVisible(dlqmessages);
                btnRestoreAll.setManaged(dlqmessages);
                btnRestoreSel.setVisible(dlqmessages);
                btnRestoreSel.setManaged(dlqmessages);

            }
        });
    }

    public void loadTreeView() {
        topSplitPane.setDisable(false);
        treeAndTableViewManager.loadTreeView();
    }


    public void log(String text) {
        Platform.runLater(() -> {
            logNode.appendText(text);
        });
    }

    public void handlePeek(ActionEvent actionEvent) {
        String entityName = getEntityName();
        if (entityName == null) return;
        var item = treeview.getSelectionModel().getSelectedItem();
        Long count = isDlq() ? getEntityProperty("deadLetterMessageCount", treeview) :
                getEntityProperty("activeMessageCount", treeview);
        if (count == 0) return;
        messageTableViewManager.peekMessages(Constants.PEEK_SIZE);
    }

    public void treeNodeSelected(ObservableValue<? extends TreeItem<DataTreeItem>> observable,
                                 TreeItem<DataTreeItem> oldValue,
                                 TreeItem<DataTreeItem> newValue) {

        if (newValue == null || newValue.getValue() == null) return;
        resetTreeContextMenu(newValue);
        messageTableViewManager.initialize();
        if (newValue.getValue().getType() == NodeType.NONE) return;
        showProgress("Loading properties for " + newValue.getValue().getName());
        CompletableFuture.runAsync(() -> treeAndTableViewManager.loadQueueProps(newValue))
                .whenComplete((v, e) -> Platform.runLater(this::hideProgress));
        handlePeek(null);
    }


    private void resetTreeContextMenu(TreeItem<DataTreeItem> newValue) {
        Map<MenuItem, List<NodeType>> map = Map.of(
                mnuSendMsg, List.of(NodeType.QUEUE, NodeType.SUBSCRIPTION),
                mnuStatus, List.of(NodeType.QUEUE, NodeType.SUBSCRIPTION,NodeType.TOPIC),
                mnuPurge, List.of(NodeType.QUEUE, NodeType.SUBSCRIPTION),
                mnuDlq, List.of(NodeType.QUEUE, NodeType.SUBSCRIPTION),
                mnuSendToDlq, List.of(NodeType.QUEUE, NodeType.SUBSCRIPTION),
                mnuSortByName, List.of(NodeType.NONE, NodeType.TOPIC),
                mnuSortByMessage, List.of(NodeType.NONE, NodeType.TOPIC)
        );
        map.forEach((k, v) -> k.setVisible(v.contains(newValue.getValue().getType())));

    }

    @FXML
    private void handleExit(ActionEvent event) {
        System.exit(0);
    }

    public void clearLog(ActionEvent actionEvent) {
        LogUtils.clearLogs();
    }

    public void handleSettings(ActionEvent actionEvent) {
        showAlert(Alert.AlertType.INFORMATION, "Work in Progress", "Coming Soon", "More settings will be available in the future.");
    }

    public void handleAbout(ActionEvent actionEvent) {
        showDialog("about-dialog.fxml", "About", null);
    }

    @FXML
    private void handleConnection(ActionEvent event) {
        showDialog("connection-dialog.fxml", "Manage Connection Strings", null);
    }

    public void handleRefresh(ActionEvent actionEvent) {
        var item = treeview.getSelectionModel().getSelectedItem();
        if (item.getValue() != null && item.getValue().getType() == NodeType.NONE) {
            loadTreeView();

        } else {
            treeNodeSelected(null, null, item);
        }
    }

    public void handleSendMsg(ActionEvent actionEvent) {
        showDialog("mesage-dialog.fxml", "Message Details", null);
    }

    public void handlePurge(ActionEvent actionEvent) {
        boolean isOk = showWarning("Warning: Mass message purge in progress! Only click ‘Purge’ if you dare", "Full Purge");
        if (!isOk) return;
        MessageUtils.purgeAllMessages(getSelectedNode());
        handleRefresh(actionEvent);
    }

    public void handlePurgeDlq(ActionEvent actionEvent) {
        boolean isOk = showWarning("All messages are about to be banished to the Dead Letter Queue. Are you feeling brave enough?", "DLQ Purge");
        if (!isOk) return;
        showProgress("Purging DLQ");
        CompletableFuture.runAsync(() -> MessageUtils.purgeAllDlqMessages(getSelectedNode()))
                .whenComplete((v, e) -> Platform.runLater(() -> {
                    hideProgress();
                    handleRefresh(actionEvent);
                }));
    }

    public void handleSortByName(ActionEvent actionEvent) {
        var item = treeview.getSelectionModel().getSelectedItem();
        if (item != null) {
            item.getChildren().sort(
                    Comparator.comparing((t) -> t.getValue().getName(), String.CASE_INSENSITIVE_ORDER)
            );
        }
    }

    public void handleSortByMessages(ActionEvent actionEvent) {
        var item = treeview.getSelectionModel().getSelectedItem();
        if (item != null) {
            item.getChildren().sort(
                    Comparator.<TreeItem<DataTreeItem>>comparingLong(t -> t.getValue().getCount()).reversed()
            );
        }
    }

    public void handleSendToDlq(ActionEvent actionEvent) {
        boolean isOk = showWarning("Oops! All messages are heading to the Dead Letter Queue. Shall we let them go?", null);
        if (!isOk) return;
        showProgress("Moving selected messages to DLQ");
        CompletableFuture.runAsync(() -> MessageUtils.moveAllMessagesToDlq(getSelectedNode()))
                .whenComplete((v, e) -> Platform.runLater(() -> {
                    hideProgress();
                    handleRefresh(actionEvent);
                }));
    }

    public String getEntityName() {
        var item = treeview.getSelectionModel().getSelectedItem();
        if (item == null || item.getValue() == null || item.getValue().getType() == NodeType.NONE) return null;
        return item.getValue().getName();
    }

    public TreeItem<DataTreeItem> getSelectedNode() {
        var item = treeview.getSelectionModel().getSelectedItem();
        if (item == null || item.getValue() == null) return null;
        return item;
    }


    public boolean isDlq() {
        String userData = (String) messageViewTabPane.getSelectionModel().getSelectedItem().getUserData();
        return userData.equals("DLQMESSAGES");
    }

    @FXML
    private void handleStyles(ActionEvent event) {
        RadioMenuItem selected = (RadioMenuItem) styleGroup.getSelectedToggle();
        String style = selected.getUserData().toString();
        ApplicationContext.getInstance().setStyle(style);
        switch (style) {
            case "PrimerLight" -> Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
            case "PrimerDark" -> Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
            case "CupertinoLight" -> Application.setUserAgentStylesheet(new CupertinoLight().getUserAgentStylesheet());
            case "CupertinoDark" -> Application.setUserAgentStylesheet(new CupertinoDark().getUserAgentStylesheet());
            case "NordLight" -> Application.setUserAgentStylesheet(new NordLight().getUserAgentStylesheet());
            case "NordDark" -> Application.setUserAgentStylesheet(new NordDark().getUserAgentStylesheet());
            case "DraculaDark" -> Application.setUserAgentStylesheet(new Dracula().getUserAgentStylesheet());
            case "Default" -> Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
            default -> System.out.println("Unknown style: " + style);
        }
    }

    public void handleRestoreFromDlq(ActionEvent actionEvent) {
        showProgress("Restoring selected messages to queue");
        var entityName = getEntityName();
        if (entityName == null || !isDlq()) return;
        ObservableList<ServiceBusReceivedMessage> selectedItems = msgDlqTableview.getSelectionModel().getSelectedItems();
        var list = new ArrayList<>(selectedItems);
        CompletableFuture.supplyAsync(() ->
                MessageUtils.moveMessagesAsyncToQueue(getSelectedNode(), list)
        ).whenComplete((result, ex) ->
                Platform.runLater(() -> {
                    if (ex != null) {
                        LogUtils.log("Error moving messages: %s", ex.getMessage());
                        showError("Move Failed", null, ex.getMessage());
                    } else {
                        result.forEach(msgDlqTableview.getItems()::remove);
                        hideProgress();
                        if (result.isEmpty()) {
                            showInfo("Move Failed", null, "Looks like the message is locked. Try refreshing and moving it again.");
                        }
                    }
                })
        );
    }

    public void handleRestoreAll(ActionEvent actionEvent) {
        showProgress("Restoring all messages to queue");
        var entityName = getEntityName();
        if (entityName == null || !isDlq()) return;
        CompletableFuture.runAsync(() -> {
                    MessageUtils.moveAllMessagesToQueue(getSelectedNode());
                })
                .whenComplete((v, e) -> Platform.runLater(() -> {
                    hideProgress();
                    msgDlqTableview.getItems().clear();
                }));

    }

    private final AtomicInteger progressCount = new AtomicInteger(0);

    public void showProgress(String message) {
        int count = progressCount.incrementAndGet();
        topSplitPane.setDisable(true);
        progressBox.setVisible(true);
        progressBox.setManaged(true);
        progressLabel.setText(message);
    }

    public void hideProgress() {
        int count = progressCount.decrementAndGet();
        if (count <= 0) {
            progressCount.set(0);
            topSplitPane.setDisable(false);
            progressBox.setVisible(false);
            progressBox.setManaged(false);
        }
    }

    public void handleSave(ActionEvent actionEvent) {
        File selectedDir = fileChooser("Select Folder to Save File", actionEvent);
        if (selectedDir == null) return;
        var sw = StopWatch.createStarted();
        var tableView = isDlq() ? msgDlqTableview : msgTableview;
        ObservableList<ServiceBusReceivedMessage> selectedItems = tableView.getSelectionModel().getSelectedItems();
        var list = new ArrayList<>(selectedItems);
        btnSaveMsg.setDisable(true);
        Thread.ofVirtual().start(() -> {
            try {
                MessageUtils.saveMessages(selectedDir, list);
                LogUtils.log("Messages(%s) saved to %s in %s", list.size(), selectedDir.getAbsolutePath(), fd(sw));
            } catch (Exception e) {
                LogUtils.log("Error saving messages: %s", e.getMessage());
            }
            Platform.runLater(() -> {
                btnSaveMsg.setDisable(false);
            });
        });
    }

    public void handleStatus(ActionEvent actionEvent) {
        AtomicReference<QueueProperties> queueProps = new AtomicReference<>();
        AtomicReference<SubscriptionProperties> subProps = new AtomicReference<>();
        AtomicReference<TopicProperties> topicProps = new AtomicReference<>();

        var item = getSelectedNode();
        if (item == null) return;
        var type = item.getValue().getType();
        String name = item.getValue().getName();
        var adminClient = ApplicationContext.getInstance().currentManager().getAdminClient();

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Change Status");
        alert.setHeaderText("Select a new status:");
        alert.setGraphic(null); // removes the big default info icon
        ComboBox<EntityStatus> comboBox = new ComboBox<>();
        comboBox.setPrefWidth(220);
        HBox box = new HBox(10);
        box.setPadding(new Insets(10, 0, 0, 0));
        box.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().addAll(new Label("Status:"), comboBox);
        HBox.setHgrow(comboBox, Priority.ALWAYS);

        alert.getDialogPane().setContent(box);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        comboBox.getItems().addAll(EntityStatus.ACTIVE, EntityStatus.DISABLED);
        EntityStatus status = null;
        switch (type) {
            case QUEUE -> {
                comboBox.getItems().addAll(EntityStatus.SEND_DISABLED,EntityStatus.RECEIVE_DISABLED);
                queueProps.set(adminClient.getQueue(name));
                status = queueProps.get().getStatus();
            }
            case TOPIC -> {
                comboBox.getItems().add(EntityStatus.SEND_DISABLED);
                topicProps.set(adminClient.getTopic(name));
                status = topicProps.get().getStatus();
            }
            case SUBSCRIPTION -> {
                comboBox.getItems().add(EntityStatus.RECEIVE_DISABLED);
                var parentName = item.getParent().getValue().getName();
                subProps.set(adminClient.getSubscription(parentName, name));
                status = subProps.get().getStatus();
            }
            default -> System.out.println("Unsupported node type: " + type);
        }
        comboBox.getSelectionModel().select(status);

        alert.showAndWait().ifPresent(buttonType -> {
            if (buttonType == ButtonType.OK) {
                EntityStatus selectedStatus = comboBox.getValue();
                if (selectedStatus == null) return;
                try {
                    switch (type) {
                        case QUEUE -> {
                            queueProps.get().setStatus(selectedStatus);
                            adminClient.updateQueue(queueProps.get());
                        }
                        case TOPIC -> {
                            topicProps.get().setStatus(selectedStatus);
                            adminClient.updateTopic(topicProps.get());
                        }
                        case SUBSCRIPTION -> {
                            subProps.get().setStatus(selectedStatus);
                            adminClient.updateSubscription(subProps.get());
                        }
                        default -> System.out.println("Unsupported node type: " + type);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println("Failed to update status for " + type + " '" + name + "': " + e.getMessage());
                }
                LogUtils.log("Status for %s %s changed to %s", type, name, selectedStatus);
            }
        });
    }
}