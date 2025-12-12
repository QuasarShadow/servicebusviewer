package com.dutils.servicebusviewer.servicebus;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceiverAsyncClient;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClientBuilder;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import com.azure.messaging.servicebus.models.SubQueue;
import com.dutils.servicebusviewer.model.NodeType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceBusManager {

    private final ServiceBusAdministrationClient adminClient;
    private final static int PREFETCH_COUNT = 20;

    public ServiceBusSenderClient getNewSenderClient(NodeType type, String queueName) {
        return senderClient.getSender(type,queueName,true);
    }
    public ServiceBusSenderClient getSenderClient(NodeType type, String queueName) {
        return senderClient.getSender(type,queueName,false);
    }

    private final SenderClient senderClient;

    public ReceiverClient getReceiverClient() {
        return receiverClient;
    }

    private final ReceiverClient receiverClient;

    public ServiceBusManager(String connectionString) {
        this.adminClient = new ServiceBusAdministrationClientBuilder().connectionString(connectionString).buildClient();
        this.receiverClient = new ReceiverClient(connectionString);
        this.senderClient = new SenderClient(connectionString);
    }

    public ServiceBusAdministrationClient getAdminClient() {
        return adminClient;
    }

    public String getNamespace() {
        return this.adminClient.getNamespaceProperties().getName();
    }

    public ServiceBusReceiverClient getReceiver(NodeType type, String entityName, String subEntityName,
                                                ServiceBusReceiveMode mode, boolean isDlq, boolean isNew) {
        return isDlq ? receiverClient.getReceiver(type, entityName, subEntityName, mode, SubQueue.DEAD_LETTER_QUEUE, isNew) :
                receiverClient.getReceiver(type, entityName, subEntityName, mode, null, isNew);
    }

    public ServiceBusReceiverClient getReceiver(NodeType type, String entityName, String subEntityName,
                                                ServiceBusReceiveMode mode, boolean isDlq) {
        return getReceiver(type, entityName, subEntityName, mode, isDlq, false);
    }

    public ServiceBusReceiverClient getNewReceiver(NodeType type, String entityName, String subEntityName,
                                                   ServiceBusReceiveMode mode, boolean isDlq) {
        return getReceiver(type, entityName, subEntityName, mode, isDlq, true);
    }

    public ServiceBusReceiverClient newReceiver(NodeType type, String entityName, String subEntityName,
                                                ServiceBusReceiveMode mode, boolean isDlq, boolean isNew) {
        return isDlq ? receiverClient.newReceiver(type, entityName, subEntityName, mode, SubQueue.DEAD_LETTER_QUEUE) :
                receiverClient.newReceiver(type, entityName, subEntityName, mode, null);
    }

    public ServiceBusReceiverAsyncClient newAsyncReceiver(NodeType type, String entityName, String subEntityName,
                                                          ServiceBusReceiveMode mode, boolean isDlq) {
        return isDlq ? receiverClient.newAsyncReceiver(type, entityName, subEntityName, mode, SubQueue.DEAD_LETTER_QUEUE) :
                receiverClient.newAsyncReceiver(type, entityName, subEntityName, mode, null);
    }

    public void closeReceiver(String queueName) {
        receiverClient.close(queueName);
    }

    public void closeAllReceivers() {
        receiverClient.closeAll();
    }

    public static class ReceiverClient {
        private final String connectionString;
        private final Map<String, ServiceBusReceiverClient> receiverMap = new ConcurrentHashMap<>();
        private final Map<String, ServiceBusReceiverClient> dlqRecieverMap = new ConcurrentHashMap<>();

        ReceiverClient(String connectionString) {
            this.connectionString = connectionString;
        }

        public ServiceBusClientBuilder.ServiceBusReceiverClientBuilder getReceiverBuilder(String queueName) {
            return new ServiceBusClientBuilder().connectionString(connectionString).receiver().queueName(queueName);
        }

        public ServiceBusReceiverClient getReceiver(NodeType type, String entityName, String subEntityName,
                                                    ServiceBusReceiveMode mode, SubQueue subQ, boolean isNew) {
            var key = java.lang.String.format("%s:%s:%s:%s:%s", type, entityName, subEntityName, mode, subQ);
            if (isNew) {
                var receiver = receiverMap.remove(key);
                if (receiver != null) receiver.close();
            }
            return receiverMap.computeIfAbsent(key, k -> newReceiver(type, entityName, subEntityName, mode, subQ));
        }

        public ServiceBusReceiverClient newReceiver(NodeType type, String entityName, String subEntityName,
                                                    ServiceBusReceiveMode mode, SubQueue subQ) {
            var builder = createReceiverBuilder(type, entityName, subEntityName, mode, subQ);
            builder.prefetchCount(PREFETCH_COUNT);
            return builder.buildClient();
        }

        public ServiceBusReceiverAsyncClient newAsyncReceiver(NodeType type, String entityName, String subEntityName,
                                                              ServiceBusReceiveMode mode, SubQueue subQ) {
            var builder = createReceiverBuilder(type, entityName, subEntityName, mode, subQ);
            builder.disableAutoComplete();
            return builder.buildAsyncClient();
        }

        private ServiceBusClientBuilder.ServiceBusReceiverClientBuilder createReceiverBuilder(NodeType type, String entityName, String subEntityName, ServiceBusReceiveMode mode, SubQueue subQ) {
            var builder = new ServiceBusClientBuilder().connectionString(connectionString).receiver();
            if (type.equals(NodeType.QUEUE)) builder.queueName(entityName);
            else builder.topicName(entityName);
            if (subEntityName != null) builder.subscriptionName(subEntityName);
            if (mode != null) builder.receiveMode(mode);
            if (subQ != null) builder.subQueue(subQ);
            return builder;
        }

        public void close(String queueName) {
            ServiceBusReceiverClient client = receiverMap.remove(queueName);
            if (client != null) {
                client.close();
            }
            closeDlq(queueName);
        }

        public void closeDlq(String queueName) {
            ServiceBusReceiverClient client = dlqRecieverMap.remove(queueName);
            if (client != null) {
                client.close();
            }
        }

        public void closeAll() {
            receiverMap.values().forEach(ServiceBusReceiverClient::close);
            dlqRecieverMap.values().forEach(ServiceBusReceiverClient::close);
            receiverMap.clear();
            dlqRecieverMap.clear();
        }
    }

    public static class SenderClient {
        private final String connectionString;
        private final Map<String, ServiceBusSenderClient> senderClientMap = new ConcurrentHashMap<>();

        public SenderClient(String connectionString) {
            this.connectionString = connectionString;
        }

        public ServiceBusSenderClient getSender(NodeType type, String entityName,boolean isNew) {
            var key = java.lang.String.format("%s:%s", type, entityName);
            if (isNew) {
                var client = senderClientMap.remove(key);
                if (client != null) client.close();
            }
            return senderClientMap.computeIfAbsent(key,
                    k -> createSenderClient(type, entityName));
        }

        private ServiceBusSenderClient createSenderClient(NodeType type, String entityName) {
            ServiceBusClientBuilder.ServiceBusSenderClientBuilder builder =
                    new ServiceBusClientBuilder()
                            .connectionString(connectionString)
                            .sender();
            if (type == NodeType.QUEUE) {
                builder.queueName(entityName);
            } else {
                builder.topicName(entityName);
            }
            return builder.buildClient();
        }

        private String buildKey(NodeType type, String entityName) {
            return type.name() + ":" + entityName;
        }

        public void closeAll() {
            senderClientMap.forEach((key, client) -> {
                try {
                    client.close();
                } catch (Exception e) {
                    System.out.printf("Failed to close sender for %s: %s%n", key, e.getMessage());
                }
            });
            senderClientMap.clear();
        }
    }}