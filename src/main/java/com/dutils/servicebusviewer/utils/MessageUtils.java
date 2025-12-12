      package com.dutils.servicebusviewer.utils;

import com.azure.core.amqp.models.AmqpMessageBodyType;
import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.models.DeadLetterOptions;
import com.dutils.servicebusviewer.config.ApplicationContext;
import com.dutils.servicebusviewer.model.DataTreeItem;
import com.dutils.servicebusviewer.model.NodeType;
import com.dutils.servicebusviewer.model.ServiceBusMessageData;
import javafx.scene.control.TreeItem;
import org.apache.commons.lang3.time.StopWatch;
import reactor.core.Disposable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static com.azure.messaging.servicebus.models.ServiceBusReceiveMode.PEEK_LOCK;
import static com.azure.messaging.servicebus.models.ServiceBusReceiveMode.RECEIVE_AND_DELETE;
import static com.dutils.servicebusviewer.utils.MapperUtils.toServiceBusMessage;
import static com.dutils.servicebusviewer.utils.Utils.fd;

public class MessageUtils {
    public static int BATCH_SIZE = 2;
    public static int TIME_OUT_SEC = 5;


    public static long receiveMatchAndComplete(ServiceBusReceiverClient receiver,
                                               BiFunction<ServiceBusReceiverClient, ServiceBusReceivedMessage, Long> completeFunction) {
        long completedCount = 0;
        var messages = receiver.receiveMessages(BATCH_SIZE, Duration.ofSeconds(TIME_OUT_SEC));
        for (ServiceBusReceivedMessage msg : messages) {
            completedCount += completeFunction.apply(receiver, msg);
        }
        return completedCount;
    }

    static DeadLetterOptions deadLetterOptions = new DeadLetterOptions()
            .setDeadLetterReason("Manual move")
            .setDeadLetterErrorDescription("Moved by Service Bus Inspector");


    public static List<ServiceBusReceivedMessage> moveMessagesAsyncToQueue(
            TreeItem<DataTreeItem> item,
            List<ServiceBusReceivedMessage> moveMessages) {
        List<ServiceBusReceivedMessage> removedMessages = new ArrayList<>();
        AtomicInteger processedCount = new AtomicInteger();
        var sw = StopWatch.createStarted();
        var entity = parseNode(item);
        var mgr = ApplicationContext.getInstance().currentManager();
        CountDownLatch countdownLatch = new CountDownLatch(1);
        try (var receiver = mgr.newAsyncReceiver(entity.type, entity.name, entity.sub, PEEK_LOCK, true)) {
            var sender = ApplicationContext.getInstance().currentManager().getNewSenderClient(entity.type, entity.name);
            Disposable subscription = receiver.receiveMessages()
                    .subscribe(
                            dlqMsg -> {
                                try {
                                    var match = moveMessages.stream()
                                            .filter(m -> m.getMessageId().equals(dlqMsg.getMessageId()))
                                            .filter(m -> m.getSequenceNumber() == dlqMsg.getSequenceNumber())
                                            .findFirst()
                                            .orElse(null);
                                    if (match == null) {
                                        return;
                                    }
                                    sender.sendMessage(cloneMessage(dlqMsg));
                                    moveMessages.remove(match);
                                    removedMessages.add(match);
                                    processedCount.incrementAndGet();
                                    receiver.complete(dlqMsg).block();
                                    LogUtils.log("Moved message: %s (seq=%d)", dlqMsg.getMessageId(), dlqMsg.getSequenceNumber());
                                    if (moveMessages.isEmpty()) {
                                        countdownLatch.countDown();
                                    }
                                } catch (Exception e) {
                                    LogUtils.log("Failed processing message %s: %s", dlqMsg.getMessageId(), e.getMessage());
                                    receiver.abandon(dlqMsg).block();
                                }
                            }
                    );
            countdownLatch.await(120, TimeUnit.SECONDS);
            subscription.dispose();
            LogUtils.log("Move completed: Source=DLQ, Destination=%s, Messages Moved=%d, Duration=%s seconds.",
                    entity.name, processedCount.get(), fd(sw));
            countdownLatch.countDown();

        } catch (Exception e) {
            LogUtils.log("Error moving messages to queue: %s", e.getMessage());
        }
        return removedMessages;
    }

    public static List<ServiceBusReceivedMessage> moveMessagesToQueue(TreeItem<DataTreeItem> item, List<ServiceBusReceivedMessage> moveMessages) {
        long count = 0;
        List<ServiceBusReceivedMessage> removedMessages = new ArrayList<>();
        var mgr = ApplicationContext.getInstance().currentManager();
        var entity = parseNode(item);
        try {
            var sender = mgr.getSenderClient(entity.type, entity.name);
            var receiver = mgr.getReceiver(entity.type, entity.name, entity.sub, PEEK_LOCK, true);
            do {
                final AtomicInteger receievdCount = new AtomicInteger(0);
                var completedCount = receiveMatchAndComplete(receiver, (rc, dlqMsg) -> {
                    System.out.println(dlqMsg.getSequenceNumber());
                    var match = moveMessages.stream()
                            .filter(m -> m.getMessageId().equals(dlqMsg.getMessageId()))
                            .filter(m -> m.getSequenceNumber() == dlqMsg.getSequenceNumber())
                            .findFirst().orElse(null);
                    if (match != null) {
                        sender.sendMessage(cloneMessage(match));
                        moveMessages.remove(match);
                        removedMessages.add(match);
                        return (long) completeMessage(receiver, dlqMsg);
                    } else {
                        receiver.abandon(dlqMsg);
                    }
                    receievdCount.incrementAndGet();
                    return 0L;
                });
                count = count + completedCount;
                LogUtils.log("InProgress: Recieved %s Moved %d DLQ messages from %s", receievdCount.get(), count, entity.name);
                if (receievdCount.get() < BATCH_SIZE || moveMessages.isEmpty()) break;
            } while (true);
            LogUtils.log("Completed: Moved to DLQ %d messages from %s", count, entity.name);
        }
        catch (Exception e) {
            LogUtils.log("Error moving messages to queue: %s", e.getMessage());
        }
        return removedMessages;
    }

    public static void moveAllMessagesToQueue(TreeItem<DataTreeItem> item) {
        StopWatch sw = StopWatch.createStarted();
        long count = 0, receivedCount;
        var mgr = ApplicationContext.getInstance().currentManager();
        var entity = parseNode(item);
        try {
            var sender = mgr.getSenderClient(entity.type, entity.name);
            var receiver = mgr.getReceiver(entity.type, entity.name, entity.sub, PEEK_LOCK, true);
            do {
                receivedCount = receiveMatchAndComplete(receiver, (rc, msg) -> {
                    sender.sendMessage(cloneMessage(msg));
                    return (long) completeMessage(rc, msg);
                });
                count = count + receivedCount;
                LogUtils.log("InProgress: Restored %d DLQ messages from %s ", count, entity.name);
            } while (receivedCount >= BATCH_SIZE);
            LogUtils.log("Completed: Restored %d DLQ messages from %s in %s secs", count, entity.name, fd(sw));
        } catch (Exception e) {
            LogUtils.log("Error moving messages to queue: %s", e.getMessage());
        }
    }

    public static void moveAllMessagesToDlq(TreeItem<DataTreeItem> item) {
        var sw = StopWatch.createStarted();
        var entity = parseNode(item);
        try {
            long count = 0, receivedCount;
            var receiver = ApplicationContext.getInstance().currentManager()
                    .getReceiver(entity.type, entity.name, entity.sub, PEEK_LOCK, false);
            do {
                receivedCount = receiveMatchAndComplete(receiver, (rc, msg) -> {
                    rc.deadLetter(msg, deadLetterOptions);
                    return 1L;
                });
                count = count + receivedCount;
                LogUtils.log("InProgress: Moved %d messages from %s", count, entity.name);
            } while (receivedCount == BATCH_SIZE);
            LogUtils.log("Competed: Moved Messages %d DLQ messages from %s in %s secs", count, entity.name, fd(sw));
        } catch (Exception e) {
            LogUtils.log("Error moving messages to DLQ: %s", e.getMessage());
        }
    }

    public static void purgeAllMessages(TreeItem<DataTreeItem> item) {
        var sw = StopWatch.createStarted();
        var entity = parseNode(item);
        try {
            long count = 0, receivedCount;
            var receiver = ApplicationContext.getInstance().currentManager()
                    .getReceiver(entity.type, entity.name, entity.sub, RECEIVE_AND_DELETE, false);

            do {
                receivedCount = receiveMatchAndComplete(receiver, MessageUtils::addCount);
                count = count + receivedCount;
                LogUtils.log("InProgress: Purged %d messages from %s", count, entity.name);
            } while (receivedCount == BATCH_SIZE);
            LogUtils.log("Completed: Purged %d   messages from %s in %s secs", count, entity.name, fd(sw));
        } catch (Exception e) {
            LogUtils.log("Error purging messages: %s", e.getMessage());
        }
    }

    public static void purgeAllDlqMessages(TreeItem<DataTreeItem> item) {
        StopWatch sw = StopWatch.createStarted();
        long count = 0, receivedCount;
        var entity = parseNode(item);
        try {
            var receiver = ApplicationContext.getInstance().currentManager()
                    .getReceiver(entity.type, entity.name, entity.sub, RECEIVE_AND_DELETE, true);
            do {
                receivedCount = receiveMatchAndComplete(receiver, MessageUtils::addCount);
                count = count + receivedCount;
                LogUtils.log("InProgress: Purged %d DLQ messages from %s ", count, entity.name);
            } while (receivedCount >= BATCH_SIZE);
            LogUtils.log("Completed: Purged  %d DLQ messages from %s in %s secs", count, entity.name, fd(sw));
        } catch (Exception e) {
            LogUtils.log("Error purging DLQ messages: %s", e.getMessage());
        }
    }


    public static int completeMessage(ServiceBusReceiverClient receiver, ServiceBusReceivedMessage message) {
        int completed = 0;
        try {
            receiver.complete(message);
            completed++;
        } catch (Exception e) {
            receiver.abandon(message);
            LogUtils.log("Error completing message: %s", e.getMessage());
        }
        return completed;
    }

    public static void resubmitNewMessage(NodeType type, String entityName, ServiceBusMessageData message) {
        var sw = StopWatch.createStarted();
        try  {
            var sender = ApplicationContext.getInstance().currentManager().getSenderClient(type,entityName);
            ServiceBusMessage serviceBusMessage = toServiceBusMessage(message);
            sender.sendMessage(serviceBusMessage);
            LogUtils.log("Message Sent to %s in %s secs", entityName, fd(sw));
        } catch (Exception e) {
            LogUtils.log("Error sending message to %s: %s", entityName, e.getMessage());
        }
    }

    public record Entity(String name, String sub, NodeType type) {
    }


    private static Entity parseNode(TreeItem<DataTreeItem> item) {
        var isSubscription = item.getValue().getType() == NodeType.SUBSCRIPTION;
        var entity = isSubscription ? item.getParent().getValue().getName() : item.getValue().getName();
        var subEntity = isSubscription ? item.getValue().getName() : "";
        return new Entity(entity, subEntity, item.getValue().getType());

    }


    public static List<ServiceBusReceivedMessage> peekMessages(NodeType type, String entityName, String subEntityName, int peekSize, boolean isDlq, long seqNum) {
        StopWatch sw = StopWatch.createStarted();
        var mgr = ApplicationContext.getInstance().currentManager();
        var receiver = mgr.getReceiver(type, entityName, subEntityName, null, isDlq);
        var messages = receiver.peekMessages(peekSize, seqNum);
        List<ServiceBusReceivedMessage> messageList = new ArrayList<>();
        messages.forEach(messageList::add);
        LogUtils.log("Completed: Peeked %d  messages from %s in %s secs", messageList.size(), entityName, fd(sw));
        return messageList;
    }


    private static ServiceBusMessage createMessage(ServiceBusReceivedMessage original, String body, Map<String, Object> customProps) {
        BinaryData bodyData = BinaryData.fromString(body);
        ServiceBusMessage message = original == null ? new ServiceBusMessage(bodyData) : toServiceBusMessage(original, bodyData);
        if (customProps != null) {
            message.getApplicationProperties().clear();
            message.getApplicationProperties().putAll(customProps);
        }
        return message;
    }

    private static ServiceBusMessage cloneMessage(ServiceBusReceivedMessage original) {
        BinaryData bodyData = switch (original.getRawAmqpMessage().getBody().getBodyType()) {
            case VALUE -> BinaryData.fromObject(original.getBody().toObject(Object.class));
            case DATA -> original.getBody();
            default -> BinaryData.fromBytes(original.getBody().toBytes());
        };
        ServiceBusMessage message = toServiceBusMessage(original, bodyData);
        if (original.getApplicationProperties() != null) {
            message.getApplicationProperties().putAll(original.getApplicationProperties());
        }
        return message;
    }

    private static Long addCount(ServiceBusReceiverClient rc, ServiceBusReceivedMessage msg) {
        return 1L;
    }

    public static String saveMessages(File folder, List<ServiceBusReceivedMessage> messages) throws IOException {
        StringBuffer retVal = new StringBuffer();
        for (var msg : messages) {
            var serviceBusMessageData = MapperUtils.toServiceBusMessageData(msg);
            String safeMessageId = msg.getMessageId() != null ? msg.getMessageId().replaceAll("[\\\\/:*?\"<>|]", "_") : "null";
            var fileName = String.format("%s-%s.json", msg.getSequenceNumber(), safeMessageId);
            LogUtils.log("Saving message to %s", fileName);
            Path filePath = folder.toPath().resolve(fileName);
            JsonFileUtil.writeToFile(filePath.toFile(), serviceBusMessageData);
            retVal.append(fileName).append("\n");
        }
        return retVal.toString();
    }

    public static String extractBody(ServiceBusReceivedMessage message) throws IOException {
        if (message == null || message.getRawAmqpMessage() == null) return null;
        AmqpMessageBodyType bodyType = message.getRawAmqpMessage().getBody().getBodyType();
        return switch (bodyType) {
            case DATA -> {
                byte[] bytes = message.getBody().toBytes();
                yield new String(bytes, StandardCharsets.UTF_8);
            }
            case VALUE -> {
                Object value = message.getRawAmqpMessage().getBody().getValue();
                yield value != null ? value.toString() : "";
            }
            case SEQUENCE -> {
                List<?> sequence = message.getRawAmqpMessage().getBody().getSequence();
                yield sequence != null ? sequence.toString() : "";
            }
        };
    }

}
