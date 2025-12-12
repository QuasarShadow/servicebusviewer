package com.dutils.servicebusviewer.model;

public enum NodeType {
    QUEUE("Queue"),
    TOPIC("Topic"),
    SUBSCRIPTION("Subscription"),
    NONE("None");

    private final String name;
    NodeType(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }
}