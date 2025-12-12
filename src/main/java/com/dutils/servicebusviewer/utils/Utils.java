

package com.dutils.servicebusviewer.utils;

import com.dutils.servicebusviewer.ServiceBusViewerApplication;
import com.dutils.servicebusviewer.codearea.JsonEditor;
import com.dutils.servicebusviewer.codearea.XMLEditor;
import com.dutils.servicebusviewer.config.ApplicationContext;
import com.dutils.servicebusviewer.model.DataTreeItem;
import com.dutils.servicebusviewer.model.NodeType;
import com.fasterxml.jackson.core.type.TypeReference;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Parent;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Function;


public class Utils {
    private static final int MAX_LOG_LINES = 1000;
    static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    public enum Type {
        ERROR,
        GENERAL,
        TIME;
    }

    public static final Function<Type, FontIcon> ICON_FOR_TYPE = type -> switch (type) {
        case ERROR -> new FontIcon(FontAwesomeSolid.EXCLAMATION_TRIANGLE);
        case TIME -> new FontIcon(FontAwesomeSolid.CLOCK);
        default -> new FontIcon(FontAwesomeSolid.INFO_CIRCLE);
    };

    public static final Function<Type, Color> BACKGROUND_COLOR_FOR_TYPE = type -> switch (type) {
        case ERROR -> Color.ORANGERED;
        case TIME -> Color.rgb(230, 245, 255);   // light blue
        default -> Color.TRANSPARENT;
    };


    public static TreeItem<DataTreeItem> createNode(String key, Object value, NodeType type, Ikon icon) {
        return createNode(key, value, type, icon, Color.BLUEVIOLET,null);
    }

    public static TreeItem<DataTreeItem> createNode(String key, Object value, NodeType type, Ikon icon, Color color,Object properties) {
        var fontIcon = new FontIcon(icon);
        fontIcon.setIconSize(16);
        fontIcon.setIconColor(color);
        Map<String, Object> mapProps = ApplicationContext.getInstance().getMapper().convertValue(value,
                new TypeReference<Map<String, Object>>() {
                });
        return new DataTreeItem(key, mapProps, type, fontIcon,properties);
    }
    //    DataTreeItem(String name, Map<String, Object> data, NodeType type, Node graphic,Object properties)
    public static TreeItem<DataTreeItem> createNode(String key, Object value, NodeType type, String image,Object properties) {
        Image icon = new Image(ServiceBusViewerApplication.class.getResourceAsStream(image));
        ImageView iconView = new ImageView(icon);
        iconView.setFitWidth(20);
        iconView.setFitHeight(20);
        Map<String, Object> mapProps = ApplicationContext.getInstance().getMapper().convertValue(value,
                new TypeReference<Map<String, Object>>() {
                });
        return new DataTreeItem(key, mapProps, type, iconView,properties);
    }
    public static <T, R> TableColumn<T, R> createColumn(
            String title,
            Function<T, R> extractor,
            double width
    ) {
        TableColumn<T, R> column = new TableColumn<>(title);
        column.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(extractor.apply(cd.getValue())));
        column.setPrefWidth(width);
        return column;
    }
    public static void status(String str) {
        ApplicationContext.getInstance().getMainUIController().statusText.setText(str);
    }
    public static String detectType(String body) {
        return body == null ? "Raw" :
                JsonEditor.isValid(body) ? "JSON" :
                        XMLEditor.isValid(body) ? "XML" : "Raw";
    }
    public static Long getEntityProperty(String name,TreeView<DataTreeItem> treeview) {
        try {
            var item = treeview.getSelectionModel().getSelectedItem();
            if (item == null || item.getValue() == null || item.getValue().getType() == NodeType.NONE) {
                return 0L;
            }
            return Long.parseLong(item.getValue().getData().getOrDefault(name, "0").toString());
        } catch (Exception e) {
            return 0L;
        }
    }
    static String cssLight= ServiceBusViewerApplication.class.getResource("light-theme.css").toExternalForm();
    static String cssDark= ServiceBusViewerApplication.class.getResource("dark-theme.css").toExternalForm();

    public static void applyStyle(Parent parent, String style){
        if (style.toUpperCase().contains("DARK")) {
            parent.getStylesheets().remove(cssLight);
            if(!parent.getStylesheets().contains(cssDark)) parent.getStylesheets().add(cssDark);
        }else{
            parent.getStylesheets().remove(cssDark);
            if(!parent.getStylesheets().contains(cssLight)) parent.getStylesheets().add(cssLight);

        }
    }

    public static String fd(StopWatch sw) {
        return DurationFormatUtils.formatDuration(sw.getDuration().toMillis(), "ss.SSS");
    }

    public static OffsetDateTime parseOffsetDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }

}



