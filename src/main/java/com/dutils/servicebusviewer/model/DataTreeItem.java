package com.dutils.servicebusviewer.model;

import javafx.scene.Node;
import javafx.scene.control.TreeItem;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class DataTreeItem extends TreeItem<DataTreeItem> {
    private final String name;
    private final Map<String, Object> data;
    private final NodeType type;
    private final Object properties;

    public DataTreeItem(String name, Map<String, Object> data, NodeType type, Node graphic, Object properties) {
        super();
        this.name = name;
        this.data = data;
        this.type = type;
        setValue(this);
        setGraphic(graphic);
        this.properties = properties;
    }

    public Object getProperties() {
        return properties;
    }


    public String getName() {
        return name;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data.clear();
        this.data.putAll(data);
    }

    public NodeType getType() {
        return type;
    }

    public Long getCount() {
        if (data == null) return 0L;
        Object value = data.getOrDefault("activeMessageCount",
                data.getOrDefault("subscriptionCount",
                        data.getOrDefault("queueSize", "0")));
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Override
    public String toString() {
        if (data == null) return name;
        List<Object> values = Arrays.asList(
                data.getOrDefault("activeMessageCount", ""),
                data.getOrDefault("subscriptionCount", ""),
                data.getOrDefault("queueSize", ""),
                data.getOrDefault("deadLetterMessageCount", "")
        );
        String metrics = values.stream()
                .map(Objects::toString)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(","));
        String size = formatSize(data.get("sizeInBytes"));

        return String.format("%s (%s) %s", name, metrics, size);
    }

    private String formatSize(Object sizeInBytes) {
        try {
            if(sizeInBytes==null) return "";
            long bytes = NumberUtils.toLong(Objects.toString(sizeInBytes, "0"));
            return  (bytes / 1024) + "Kb";
        } catch (Exception e) {
            return "";
        }

    }
}


