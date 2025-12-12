package com.dutils.servicebusviewer.config;

import com.dutils.servicebusviewer.MainUIController;
import com.dutils.servicebusviewer.servicebus.ServiceBusManager;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.HashMap;
import java.util.Map;

public class ApplicationContext {

    private static final ApplicationContext INSTANCE = new ApplicationContext();

    private final Map<String, ServiceBusManager> managers = new HashMap<>();
    private ServiceBusManager currentManager;
    private String currentNamespace;

    public String getStyle() {
        return style;
    }

    private String style = "none";

    public ObjectMapper getMapper() {
        return mapper;
    }

    public ObjectMapper mapper = new ObjectMapper();


    private MainUIController mainUIController;
    private ApplicationContext() {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.registerModule(new JavaTimeModule());
    }

    public static ApplicationContext getInstance() {
        return INSTANCE;
    }

    public ServiceBusManager registerManager(String connectionString) {
        ServiceBusManager manager = new ServiceBusManager(connectionString);
        managers.put(manager.getNamespace(), manager);
        selectNamespace(manager.getNamespace());
        return manager;
    }

    public void selectNamespace(String namespace) {
        this.currentNamespace = namespace;
        this.currentManager = managers.get(namespace);
    }

    public ServiceBusManager currentManager() {
        return currentManager;
    }

    public String currentNamespace() {
        return currentNamespace;
    }

    public Map<String, ServiceBusManager> managers() {
        return Map.copyOf(managers);
    }

    public MainUIController getMainUIController() {
        return mainUIController;
    }

    public void setMainUIController(MainUIController mainUIController) {
        this.mainUIController = mainUIController;
    }

    public void setStyle(String style) {
        this.style=style;
    }
}