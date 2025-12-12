
package com.dutils.servicebusviewer.mgr;

import com.azure.core.http.rest.PagedIterable;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.models.*;
        import com.dutils.servicebusviewer.config.ApplicationContext;
import com.dutils.servicebusviewer.model.DataTreeItem;
import com.dutils.servicebusviewer.model.NodeType;
import com.fasterxml.jackson.core.type.TypeReference;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.paint.Color;
import javafx.util.Pair;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.time.StopWatch;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.dutils.servicebusviewer.utils.Utils.*;
        import static java.lang.String.format;
import static java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor;
import static javafx.application.Platform.runLater;
import static org.apache.commons.lang3.time.DurationFormatUtils.formatDuration;
import static org.kordamp.ikonli.fontawesome6.FontAwesomeSolid.*;

public class MainUITreeAndTableViewManager {

    private final TreeView<DataTreeItem> treeView;

    private final TableView<Pair<String, String>> tableView;
    ApplicationContext context = ApplicationContext.getInstance();

    public MainUITreeAndTableViewManager(TreeView<DataTreeItem> treeView, TableView<Pair<String, String>> tableView) {
        this.treeView = treeView;
        this.tableView = tableView;
    }

    public void loadQueueProps(TreeItem<DataTreeItem> newValue) {
        StopWatch sw = StopWatch.createStarted();
        try {
            String selectedItem = newValue.getValue().getName();
            ObservableList<Pair<String, String>> data = loadEntityProperties(newValue);
            runLater(() -> {
                ApplicationContext.getInstance().getMainUIController().statusText.setText("Selected: " + selectedItem);
                initializeTableColumns();
                tableView.setItems(data);
                com.dutils.servicebusviewer.utils.LogUtils.log("%s Props Loaded in %s Seconds", selectedItem, fd(sw));
            });
        } catch (Exception e) {
            runLater(() -> com.dutils.servicebusviewer.utils.LogUtils.log("Error loading properties: %s ", e.getMessage()));
        }
    }


    private void initializeTableColumns() {
        if (tableView.getColumns().isEmpty()) {
            tableView.getColumns().addAll(createColumn("Property", Pair::getKey, 300),
                    createColumn("Value", Pair::getValue, 500));
        } else {
            tableView.getItems().clear();
        }
    }


    public ObservableList<Pair<String, String>> loadEntityProperties(TreeItem<DataTreeItem> newValue) {
        var sw = StopWatch.createStarted();
        ServiceBusAdministrationClient adminClient = ApplicationContext.getInstance().currentManager().getAdminClient();
        ObservableList<Pair<String, String>> tableData = FXCollections.observableArrayList();
        DataTreeItem item = newValue.getValue();
        String entityName = item.getName();
        switch (item.getType()) {
            case NodeType.QUEUE:
                QueueRuntimeProperties queueRuntime = adminClient.getQueueRuntimeProperties(entityName);
                QueueProperties queueProps = (QueueProperties) item.getProperties();
                var qMap = objectToTableData(queueRuntime, tableData, "Runtime.");
                Platform.runLater(() -> newValue.setValue(new DataTreeItem(entityName, qMap, item.getType(), item.getGraphic(), queueProps)));
                objectToTableData(queueProps, tableData, "");
                break;
            case NodeType.TOPIC:
                TopicRuntimeProperties topicRuntime = adminClient.getTopicRuntimeProperties(entityName);
                TopicProperties topicProps = (TopicProperties) item.getProperties();
                var topicMap = objectToTableData(topicRuntime, tableData, "Runtime.");
                Platform.runLater(() -> newValue.setValue(new DataTreeItem(entityName, topicMap, item.getType(), item.getGraphic(), topicProps)));
                objectToTableData(topicProps, tableData, "");
                break;
            case NodeType.SUBSCRIPTION:
                var  topicName = newValue.getParent().getValue().getName();
                var subRuntimeProps = adminClient.getSubscriptionRuntimeProperties(topicName, newValue.getValue().getName());
                SubscriptionProperties subsProps = (SubscriptionProperties) item.getProperties();
                var subsMap = objectToTableData(subRuntimeProps, tableData, "Runtime.");
                Platform.runLater(() -> newValue.setValue(new DataTreeItem(entityName, subsMap, item.getType(), item.getGraphic(), subsProps)));
                break;
        }
        Platform.runLater(() -> com.dutils.servicebusviewer.utils.LogUtils.log("Loading Runtime Properties of %s took %s Secs", entityName, fd(sw)));
        return tableData;
    }


    Map<String, Object> objectToTableData(Object obj, ObservableList<Pair<String, String>> data, String prefix) {
        Map<String, Object> map = context.getMapper().convertValue(obj, new TypeReference<Map<String, Object>>() {
        });
        map.forEach((k, v) -> data.add(new Pair<>(format("%s%s", prefix, k), String.valueOf(v))));
        return map;
    }

    public void loadTreeView() {
        var sw = StopWatch.createStarted();
        var ns = context.currentManager().getNamespace();
        var rootItem = createNode(ns, null, NodeType.NONE, "/icons/asb.png", context.currentManager().getAdminClient().getNamespaceProperties());
        rootItem.setExpanded(true);
        rootItem.getChildren().add(loadQueues());
        rootItem.getChildren().add(loadTopics());
        treeView.setRoot(rootItem);
        com.dutils.servicebusviewer.utils.LogUtils.log(format("Tree Loaded in  %s  Seconds", formatDuration(sw.getDuration().toMillis(), "ss.SSS")));
    }

    public TreeItem<DataTreeItem> loadQueues() {
        StopWatch sw = StopWatch.createStarted();
        final ServiceBusAdministrationClient adminClient = context.currentManager().getAdminClient();
        var size = Map.of("queueSize", adminClient.listQueues().stream().count());
        var queueNode = createNode("Queues", size, NodeType.NONE, LAYER_GROUP);

        Map<String, List<QueueProperties>> grouped = adminClient.listQueues().stream().collect(Collectors.groupingBy(q -> q.getName().split("\\.")[0]));

        try (var executor = newVirtualThreadPerTaskExecutor()) {
            grouped.forEach((key, value) -> {
                TreeItem<DataTreeItem> node;
                if (CollectionUtils.size(value) == 1 && key.equals(CollectionUtils.get(value, 0).getName())) {
                    node = queueNode;
                } else {
                    node = createNode(key, null, NodeType.NONE, BOX, Color.MEDIUMVIOLETRED, adminClient.getNamespaceProperties());
                    queueNode.getChildren().add(node);
                }
                value.forEach(props -> CompletableFuture.runAsync(() -> processQueue(adminClient, props, node), executor));
            });
        }
        com.dutils.servicebusviewer.utils.LogUtils.log("Queues Loaded in  %s  Seconds", fd(sw));
        queueNode.setExpanded(true);
        return queueNode;
    }

    static void processQueue(ServiceBusAdministrationClient adminClient, QueueProperties staticprops, TreeItem<DataTreeItem> queueNode) {
        var sw = StopWatch.createStarted();
        QueueRuntimeProperties qProps = adminClient.getQueueRuntimeProperties(staticprops.getName());
        runLater(() -> queueNode.getChildren().add(createNode(staticprops.getName(), qProps, NodeType.QUEUE, ENVELOPE, Color.BLUEVIOLET, staticprops)));
        com.dutils.servicebusviewer.utils.LogUtils.log("Time %s — Queue: %s", fd(sw), staticprops.getName());
    }

    public TreeItem<DataTreeItem> loadTopics() {
        final ServiceBusAdministrationClient adminClient = context.currentManager().getAdminClient();
        var topicsNode = createNode("Topics", null, NodeType.NONE, DATABASE, Color.DARKBLUE, adminClient.getNamespaceProperties());
        try (var executor = newVirtualThreadPerTaskExecutor()) {
            adminClient.listTopics().forEach(props -> CompletableFuture.runAsync(() -> processTopics(props, adminClient, topicsNode), executor));
        }
        return topicsNode;
    }

    private static void processTopics(TopicProperties staticProps, ServiceBusAdministrationClient adminClient, TreeItem<DataTreeItem> mainTopicsNode) {
        var sw = StopWatch.createStarted();
        TopicRuntimeProperties tProps = adminClient.getTopicRuntimeProperties(staticProps.getName());
        PagedIterable<SubscriptionProperties> subscriptions = adminClient.listSubscriptions(staticProps.getName());
        var runtimePropsMap = subscriptions.stream()
                .collect(Collectors.toMap(
                        SubscriptionProperties::getSubscriptionName,
                        sub -> adminClient.getSubscriptionRuntimeProperties(staticProps.getName(), sub.getSubscriptionName())
                ));
        runLater(() -> {
            var specificTopicNode = createNode(staticProps.getName(), tProps, NodeType.TOPIC, HOCKEY_PUCK, Color.DARKBLUE, staticProps);
            mainTopicsNode.getChildren().add(specificTopicNode);
            subscriptions.forEach((subStaticProps) -> {
                var subsProps = runtimePropsMap.get(subStaticProps.getSubscriptionName());
                specificTopicNode.getChildren().add(createNode(
                        subStaticProps.getSubscriptionName(), subsProps, NodeType.SUBSCRIPTION, ENVELOPE, Color.DARKBLUE, subStaticProps));
            });
        });
        com.dutils.servicebusviewer.utils.LogUtils.log("Loaded %s topic in %s secs.", fd(sw), staticProps.getName());
    }


}


