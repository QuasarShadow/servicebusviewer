module com.dutils.servicebusviewer {
    // ✅ JavaFX modules
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.base;
    requires java.desktop;
    requires java.logging;

    // ✅ JavaFX 3rd-party UI libs
    requires org.controlsfx.controls;
    requires org.fxmisc.richtext;
    requires org.fxmisc.flowless;
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome6;

    // ✅ Azure SDK (automatic modules)
    requires com.azure.core;
    requires com.azure.core.amqp;
    requires com.azure.json;
    requires com.azure.identity;
    requires com.azure.messaging.servicebus;
    requires com.azure.http.netty;
    requires com.azure.xml;


    // ✅ Jackson (automatic modules)
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;

    // ✅ Commons
    requires org.apache.commons.lang3;
    requires org.apache.commons.collections4;
    requires atlantafx.base;
    requires java.sql;

    // ✅ Your own packages
    opens com.dutils.servicebusviewer to javafx.fxml;
    exports com.dutils.servicebusviewer;
    exports com.dutils.servicebusviewer.model;
    exports com.dutils.servicebusviewer.mgr;
    opens com.dutils.servicebusviewer.mgr to javafx.fxml;

}