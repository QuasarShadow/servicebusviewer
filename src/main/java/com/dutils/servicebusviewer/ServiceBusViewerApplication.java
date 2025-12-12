package com.dutils.servicebusviewer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public class ServiceBusViewerApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(ServiceBusViewerApplication.class.getResource("main-ui.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1000, 800); // Set width and height
        stage.setTitle("Azure Service Bus Viewer");
        stage.setScene(scene);
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/icons/sbinspect.png")));
        URL cssResource = ServiceBusViewerApplication.class.getResource("light-theme.css");
        scene.getStylesheets().add(cssResource.toExternalForm());
        stage.show();
    }
}
