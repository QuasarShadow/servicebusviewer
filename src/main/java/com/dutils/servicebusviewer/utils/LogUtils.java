package com.dutils.servicebusviewer.utils;

import com.dutils.servicebusviewer.config.ApplicationContext;
import javafx.application.Platform;
import javafx.scene.paint.Color;

public class LogUtils {
    public static void log(String msg, Color color) {
        log(msg, color, Utils.Type.GENERAL);
    }

    public static void log(String msg, Color color, Utils.Type type) {
        var logComponent = ApplicationContext.getInstance().getMainUIController();
        Platform.runLater(() -> logComponent.log(msg + System.lineSeparator()));
    }

    public static void log(String msg, Utils.Type type) {
        log(msg, null, type);
    }

    public static void log(String msg) {
        log(msg, null, Utils.Type.GENERAL);
    }

    public static void log(String msg, Object... values) {
        String log = String.format(msg, values);
        log(log, null, Utils.Type.GENERAL);
    }
    public static void clearLogs() {
        ApplicationContext.getInstance().getMainUIController().logNode.clear();
    }

}
