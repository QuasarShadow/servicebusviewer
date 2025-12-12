
package com.dutils.servicebusviewer.model;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

public class ServiceBusMessageData {

    private String messageId;
    private String contentType;
    private String correlationId;
    private String subject;
    private String to;
    private String replyTo;
    private String replyToSessionId;
    private String sessionId;
    private String partitionKey;
    private String transactionPartitionKey;
    private Duration timeToLive;
    private OffsetDateTime scheduledEnqueueTime;
    private Map<String, Object> applicationProperties = new HashMap<>();
    private String body;

    public ServiceBusMessageData() {}

    public ServiceBusMessageData(String body) {
        this.body = body;
    }


    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    public String getReplyToSessionId() {
        return replyToSessionId;
    }

    public void setReplyToSessionId(String replyToSessionId) {
        this.replyToSessionId = replyToSessionId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public void setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    public String getTransactionPartitionKey() {
        return transactionPartitionKey;
    }

    public void setTransactionPartitionKey(String transactionPartitionKey) {
        this.transactionPartitionKey = transactionPartitionKey;
    }

    public Duration getTimeToLive() {
        return timeToLive;
    }

    public void setTimeToLive(Duration timeToLive) {
        this.timeToLive = timeToLive;
    }

    public OffsetDateTime getScheduledEnqueueTime() {
        return scheduledEnqueueTime;
    }

    public void setScheduledEnqueueTime(OffsetDateTime scheduledEnqueueTime) {
        this.scheduledEnqueueTime = scheduledEnqueueTime;
    }

    public Map<String, Object> getApplicationProperties() {
        return applicationProperties;
    }

    public void setApplicationProperties(Map<String, Object> applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }


    public ServiceBusMessageData addProperty(String key, Object value) {
        this.applicationProperties.put(key, value);
        return this;
    }

    @Override
    public String toString() {
        return "ServiceBusMessageData{" +
                "messageId='" + messageId + '\'' +
                ", contentType='" + contentType + '\'' +
                ", correlationId='" + correlationId + '\'' +
                ", subject='" + subject + '\'' +
                ", to='" + to + '\'' +
                ", replyTo='" + replyTo + '\'' +
                ", replyToSessionId='" + replyToSessionId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", partitionKey='" + partitionKey + '\'' +
                ", transactionPartitionKey='" + transactionPartitionKey + '\'' +
                ", timeToLive=" + timeToLive +
                ", scheduledEnqueueTime=" + scheduledEnqueueTime +
                ", applicationProperties=" + applicationProperties +
                ", body='" + body + '\'' +
                '}';
    }
}