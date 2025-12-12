package com.dutils.servicebusviewer.utils;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.dutils.servicebusviewer.model.ServiceBusMessageData;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class MapperUtils {

    public static ServiceBusMessageData toServiceBusMessageData(ServiceBusReceivedMessage original) {
        if (original == null) return null;
        ServiceBusMessageData data = new ServiceBusMessageData();
        data.setMessageId(original.getMessageId());
        data.setContentType(original.getContentType());
        data.setCorrelationId(original.getCorrelationId());
        data.setSubject(original.getSubject());
        data.setTo(original.getTo());
        data.setReplyTo(original.getReplyTo());
        data.setReplyToSessionId(original.getReplyToSessionId());
        data.setSessionId(original.getSessionId());
        data.setPartitionKey(original.getPartitionKey());
        data.setTransactionPartitionKey(original.getPartitionKey());
        data.setTimeToLive(original.getTimeToLive());
        data.setScheduledEnqueueTime(original.getScheduledEnqueueTime());
        if (original.getApplicationProperties() != null) {
            data.getApplicationProperties().putAll(original.getApplicationProperties());
        }
        try {
            data.setBody(MessageUtils.extractBody(original));
        } catch (IOException e) {
            LogUtils.log("Exception Occured: %s", e.getMessage());
        }
        return data;
    }

    public static ServiceBusMessage toServiceBusMessage(ServiceBusMessageData data) {
        if (data == null) return null;
        BinaryData bodyData = data.getBody() != null ? BinaryData.fromString(data.getBody()) : BinaryData.fromBytes(new byte[0]);
        ServiceBusMessage msg = new ServiceBusMessage(bodyData);

        if (data.getMessageId() != null) msg.setMessageId(data.getMessageId());
        if (data.getContentType() != null) msg.setContentType(data.getContentType());
        if (data.getCorrelationId() != null) msg.setCorrelationId(data.getCorrelationId());
        if (data.getSubject() != null) msg.setSubject(data.getSubject());
        if (data.getTo() != null) msg.setTo(data.getTo());
        if (data.getReplyTo() != null) msg.setReplyTo(data.getReplyTo());
        if (data.getReplyToSessionId() != null) msg.setReplyToSessionId(data.getReplyToSessionId());
        if (data.getSessionId() != null) msg.setSessionId(data.getSessionId());

        if (data.getTimeToLive() != null) msg.setTimeToLive(data.getTimeToLive());
        if (data.getScheduledEnqueueTime() != null) msg.setScheduledEnqueueTime(data.getScheduledEnqueueTime());
        if (data.getPartitionKey() != null) msg.setPartitionKey(data.getPartitionKey());

        if (data.getApplicationProperties() != null ) {
            msg.getApplicationProperties().clear();
            msg.getApplicationProperties().putAll(data.getApplicationProperties());
        }
        return msg;
    }

    static ServiceBusMessage toServiceBusMessage(ServiceBusReceivedMessage original, BinaryData bodyData){
        ServiceBusMessage message = new ServiceBusMessage(bodyData);
        message.setMessageId(original.getMessageId());
        message.setContentType(original.getContentType());
        message.setCorrelationId(original.getCorrelationId());
        message.setSubject(original.getSubject());
        message.setTo(original.getTo());
        message.setReplyTo(original.getReplyTo());
        message.setReplyToSessionId(original.getReplyToSessionId());
        message.setSessionId(original.getSessionId());
        message.setTimeToLive(original.getTimeToLive());
        message.setScheduledEnqueueTime(original.getScheduledEnqueueTime());
        message.setPartitionKey(original.getPartitionKey());
        if (original.getRawAmqpMessage().getMessageAnnotations() != null ) {
            original.getRawAmqpMessage().getMessageAnnotations()
                    .forEach(message.getRawAmqpMessage().getMessageAnnotations()::put);
        }
        if (original.getRawAmqpMessage().getDeliveryAnnotations() != null) {
            original.getRawAmqpMessage().getDeliveryAnnotations()
                    .forEach(message.getRawAmqpMessage().getDeliveryAnnotations()::put);
        }
        return message;
    }
    public static Map<String, Object> toMap(ServiceBusReceivedMessage msg) {
        return new MapBuilder()
                .putIfNotNull("messageId", msg.getMessageId())
                .putIfNotNull("correlationId", msg.getCorrelationId())
                .putIfNotNull("sessionId", msg.getSessionId())
                .putIfNotNull("replyToSessionId", msg.getReplyToSessionId())
                .putIfNotNull("partitionKey", msg.getPartitionKey())
                .putIfNotNull("deliveryCount", msg.getDeliveryCount())
                .putIfNotNull("sequenceNumber", msg.getSequenceNumber())
                .putIfNotNull("enqueuedSequenceNumber", msg.getEnqueuedSequenceNumber())
                .putIfNotNull("to", msg.getTo())
                .putIfNotNull("replyTo", msg.getReplyTo())
                .putIfNotNull("subject", msg.getSubject())
                .putIfNotNull("contentType", msg.getContentType())
                .putIfNotNull("state", msg.getState())
                .putIfNotNull("scheduledEnqueueTime", msg.getScheduledEnqueueTime())
                .putIfNotNull("enqueuedTime", msg.getEnqueuedTime())
                .putIfNotNull("lockedUntil", msg.getLockedUntil())
                .putIfNotNull("expiresAt", msg.getExpiresAt())
                .putIfNotNull("timeToLive", msg.getTimeToLive())
                .putIfNotNull("deadLetterReason", msg.getDeadLetterReason())
                .putIfNotNull("deadLetterErrorDescription", msg.getDeadLetterErrorDescription())
                .putIfNotNull("deadLetterSource", msg.getDeadLetterSource())
                .build();
    }
    public static Map<String, Object> toMap(ServiceBusMessageData data) {
        return new MapBuilder()
                .putIfNotNull("messageId", data.getMessageId())
                .putIfNotNull("correlationId", data.getCorrelationId())
                .putIfNotNull("sessionId", data.getSessionId())
                .putIfNotNull("replyToSessionId", data.getReplyToSessionId())
                .putIfNotNull("partitionKey", data.getPartitionKey())
                .putIfNotNull("to", data.getTo())
                .putIfNotNull("replyTo", data.getReplyTo())
                .putIfNotNull("subject", data.getSubject())
                .putIfNotNull("contentType", data.getContentType())
                .putIfNotNull("scheduledEnqueueTime", data.getScheduledEnqueueTime())
                .putIfNotNull("timeToLive", data.getTimeToLive())
                .build();
    }

    static class MapBuilder {
        private final Map<String, Object> map = new LinkedHashMap<>();

        public MapBuilder putIfNotNull(String key, Object value) {
            if (value != null) map.put(key, value);
            return this;
        }

        public Map<String, Object> build() {
            return map;
        }
    }
}

