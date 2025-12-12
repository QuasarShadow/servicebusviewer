package com.dutils.servicebusviewer.mgr;

import com.azure.core.amqp.models.AmqpMessageBodyType;
import com.azure.core.util.IterableStream;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.dutils.servicebusviewer.config.ApplicationContext;
import com.dutils.servicebusviewer.model.NodeType;
import com.dutils.servicebusviewer.utils.LogUtils;
import com.dutils.servicebusviewer.utils.MessageUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

import static com.dutils.servicebusviewer.utils.DialogUtils.showDialog;
import static com.dutils.servicebusviewer.utils.Utils.createColumn;

public class MainUIMessageTableViewManager {
    private final TableView<ServiceBusReceivedMessage> msgTableview;
    private final TableView<ServiceBusReceivedMessage> msgDlqTableview;
    private long lastSequenceNumber = -1;
    private boolean isPeekingActive;
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public MainUIMessageTableViewManager(TableView<ServiceBusReceivedMessage> msgTableview, TableView<ServiceBusReceivedMessage> msgDlqTableview) {
        this.msgTableview = msgTableview;
        this.msgDlqTableview = msgDlqTableview;
    }

    public void initialize() {
        lastSequenceNumber = -1;
        isPeekingActive = false;
        try {
            if (peekFuture != null && !peekFuture.isDone()) {
                peekFuture.cancel(true);
            }
        } catch (Exception e) {
            LogUtils.log("Error cancelling peekFuture: %s", e.getMessage());
        }
        setupTableview(msgTableview);
        setupTableview(msgDlqTableview);
    }

    private void setupTableview(TableView<ServiceBusReceivedMessage> tableView) {
        ObservableList<ServiceBusReceivedMessage> messages = FXCollections.observableArrayList();
        tableView.setItems(messages);
        tableView.getColumns().clear();
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableView.getColumns().addAll(
                createColumn("Message Id", ServiceBusReceivedMessage::getMessageId, 50),
                createColumn("Sequence", ServiceBusReceivedMessage::getSequenceNumber, 20),
                createColumn("Subject", ServiceBusReceivedMessage::getSubject, 50),
                createColumn("Delivery Count", ServiceBusReceivedMessage::getDeliveryCount, 20),
                createColumn("Body Size", MainUIMessageTableViewManager::getMessageBodySize, 20),
                createColumn("Type", d -> d.getRawAmqpMessage().getBody().getBodyType(), 20),
                createColumn("Enqueued Time", d -> d.getEnqueuedTime() != null ? d.getEnqueuedTime().format(formatter) : "", 100)
        );
        addTableViewListener(tableView);
    }

    public static int getMessageBodySize(ServiceBusReceivedMessage message) {
        var raw = message.getRawAmqpMessage();
        AmqpMessageBodyType type = raw.getBody().getBodyType();
        return switch (type) {
            case DATA -> {
                IterableStream<byte[]> dataStream = raw.getBody().getData();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                for (byte[] chunk : dataStream) {
                    baos.write(chunk, 0, chunk.length);
                }
                yield baos.size();
            }
            case VALUE -> {
                Object value = raw.getBody().getValue();
                yield (value == null) ? 0 : value.toString().getBytes().length;
            }
            case SEQUENCE -> {
                var seq = raw.getBody().getSequence();
                yield (seq == null) ? 0 : seq.toString().getBytes().length;
            }
            default -> 0;
        };
    }

    private void addTableViewListener(TableView<ServiceBusReceivedMessage> tableView) {

        tableView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                handleShowMessageDetails(tableView);
            }
        });

        // Return key handler
        tableView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                handleShowMessageDetails(tableView);
                event.consume();
            }
        });

    }

    private void handleShowMessageDetails(TableView<ServiceBusReceivedMessage> tableView) {
        var selectedItem = tableView.getSelectionModel().getSelectedItem();
        if (selectedItem == null) return;
        showDialog("message-dialog.fxml", "Message Details", selectedItem);
    }

    public CompletableFuture<?> peekFuture;

    public void peekMessages( int count) {
        var mainUi = ApplicationContext.getInstance().getMainUIController();
        var item = mainUi.getSelectedNode();
        var type = item.getValue().getType();
        var isSubscription = type==NodeType.SUBSCRIPTION;
        var entity = isSubscription? item.getParent().getValue().getName():item.getValue().getName();
        var subEntity = isSubscription? item.getValue().getName():null;

        boolean isDlq = mainUi.isDlq();
        var tableView = isDlq ? msgDlqTableview : msgTableview;

        isPeekingActive = true;
        mainUi.showProgress("Peeking Messages");

        peekFuture = CompletableFuture.runAsync(() ->
                MessageUtils.peekMessages(type, entity, subEntity, count, isDlq, lastSequenceNumber + 1)
                        .forEach(msg -> {
                            runActive(() -> tableView.getItems().add(msg));
                            lastSequenceNumber = msg.getSequenceNumber();
                        })
        ).exceptionally(e -> {
            LogUtils.log("Error peeking messages: %s", e.getMessage());
            return null;
        }).whenComplete((result, ex) -> mainUi.hideProgress());
    }

    void runActive(Runnable r) {
        Platform.runLater(() -> {
            if (isPeekingActive)
                r.run();
        });
    }
}



