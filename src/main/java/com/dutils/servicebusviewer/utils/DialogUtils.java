package com.dutils.servicebusviewer.utils;

import com.dutils.servicebusviewer.ServiceBusViewerApplication;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

import static com.dutils.servicebusviewer.utils.LogUtils.log;

public class DialogUtils {
    public static void showInfo(String title, String header, String message) {
        showAlert(Alert.AlertType.INFORMATION, title, header, message);
    }

    public static void showError(String title, String header, String message) {
        showAlert(Alert.AlertType.ERROR, title, header, message);
    }

    public static boolean showWarning(String message, String title) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Warning");
        alert.setHeaderText(title);
        alert.setContentText(message);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    public static void showAlert(Alert.AlertType type, String title, String header, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.setResizable(true);
        alert.getDialogPane().setPrefWidth(500);
        alert.showAndWait();
    }

    public static <T> void showDialog(String fxml, String title, T contextObject) {
        try {
            FXMLLoader loader = new FXMLLoader(ServiceBusViewerApplication.class.getResource(fxml));
            DialogPane dialogPane = loader.load();
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setDialogPane(dialogPane);
            dialog.setTitle(title);
            dialog.setResizable(true);
            dialog.initModality(Modality.APPLICATION_MODAL);
            try {
                if (contextObject != null) {
                    Object controller = loader.getController();
                    Method customInit = controller.getClass().getMethod("init", contextObject.getClass());
                    customInit.invoke(controller, contextObject);
                }
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
                log("No custom initialization method found in %s", fxml);
            }

            dialog.showAndWait();

        } catch (IOException e) {
            log("Error loading %s%n", fxml);
            e.printStackTrace();
        }
    }

    public static File fileChooser(String title, Event actionEvent) {
        Stage stage = (Stage) ((Node) actionEvent.getSource()).getScene().getWindow();
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle(title);
        File selectedDir = dirChooser.showDialog(stage);
        if (selectedDir == null) {
            return null;
        }
        return selectedDir;
    }
}
