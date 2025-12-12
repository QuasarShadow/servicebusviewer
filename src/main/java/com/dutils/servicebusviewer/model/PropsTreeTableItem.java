package com.dutils.servicebusviewer.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class PropsTreeTableItem {

    private final StringProperty key = new SimpleStringProperty();
    private final StringProperty value = new SimpleStringProperty();
    private final BooleanProperty editableKey = new SimpleBooleanProperty(true);
    private final BooleanProperty editableValue = new SimpleBooleanProperty(true);
    private final String fieldName ;

    public PropsTreeTableItem(String fieldName, String key, Object value, boolean editableKey, boolean editableValue) {
        this.key.set(key);
        if (value == null) { value = "";}
        this.value.set(value.toString());
        this.editableKey.set(editableKey);
        this.editableValue.set(editableValue);
        this.fieldName = fieldName;
    }

    public String getKey() {
        return key.get();
    }

    public void setKey(String key) {
        this.key.set(key);
    }

    public StringProperty keyProperty() {
        return key;
    }

    public String getValue() {
        return value.get();
    }

    public void setValue(String value) {
        this.value.set(value);
    }

    public StringProperty valueProperty() {
        return value;
    }

    public boolean isEditableKey() {
        return editableKey.get();
    }

    public void setEditableKey(boolean editable) {
        this.editableKey.set(editable);
    }

    public BooleanProperty editableKeyProperty() {
        return editableKey;
    }

    public boolean isEditableValue() {
        return editableValue.get();
    }

    public void setEditableValue(boolean editable) {
        this.editableValue.set(editable);
    }

    public BooleanProperty editableValueProperty() {
        return editableValue;
    }

    public String getFieldName() {
        return fieldName;
    }
}