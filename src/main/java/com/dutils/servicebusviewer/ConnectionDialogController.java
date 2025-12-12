package com.dutils.servicebusviewer;

import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClientBuilder;
import com.dutils.servicebusviewer.config.ApplicationContext;
import com.dutils.servicebusviewer.utils.LogUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.regex.Pattern;

import static com.dutils.servicebusviewer.utils.DialogUtils.showError;
import static com.dutils.servicebusviewer.utils.DialogUtils.showInfo;
import static java.lang.String.format;
import static org.apache.commons.lang3.time.DurationFormatUtils.formatDuration;

public class ConnectionDialogController {
    Pattern pattern = Pattern.compile("sb://([^.]+)\\.servicebus\\.windows\\.net");

    private static final String CONFIG_DIR = ".dutils";
    private static final String PROPERTIES_FILE = "connections.properties";
    @FXML
    public ButtonType btnConnect;
    @FXML
    public ButtonType btnTest;


    @FXML
    public TextField txtConn;
    @FXML
    public Button btnSave;

    @FXML
    private TableView<ConnectionEntry> dlgTableView;

    @FXML
    private Button btnAdd;


    @FXML
    private Button btnRemove;

    @FXML
    private DialogPane dialogPane;

    private ObservableList<ConnectionEntry> data;

    @FXML
    public void initialize() {
        dialogPane.lookupButton(btnConnect).addEventFilter(ActionEvent.ACTION, e -> connectAction());
        dialogPane.lookupButton(btnTest).addEventFilter(ActionEvent.ACTION, this::testConnection);

        data = FXCollections.observableArrayList();

        // Setup columns
        dlgTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<ConnectionEntry, String> nameSpaceColumn = (TableColumn<ConnectionEntry, String>) dlgTableView.getColumns().get(0);
        nameSpaceColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().nameSpace()));
        nameSpaceColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        nameSpaceColumn.setOnEditCommit(event -> {
            int index = data.indexOf(event.getRowValue());
            data.set(index, new ConnectionEntry(event.getNewValue(), event.getRowValue().connectionString()));
        });

        TableColumn<ConnectionEntry, String> connectionStringColumn = (TableColumn<ConnectionEntry, String>) dlgTableView.getColumns().get(1);
        connectionStringColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().connectionString()));
        connectionStringColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        connectionStringColumn.setOnEditCommit(event -> {
            int index = data.indexOf(event.getRowValue());
            data.set(index, new ConnectionEntry(event.getRowValue().nameSpace(), event.getNewValue()));
        });

        dlgTableView.setEditable(true);
        dlgTableView.setItems(data);
        dlgTableView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        btnAdd.setOnAction(e -> addRow());
        btnRemove.setOnAction(e -> removeRow());
        btnSave.setOnAction(e -> saveToProperties());

        loadFromProperties();
        dlgTableView.getSelectionModel().selectFirst();

    }


    private void addRow() {
        if (StringUtils.isNotBlank(txtConn.getText())) {
            java.util.regex.Matcher matcher = pattern.matcher(txtConn.getText());
            var nameSpace = matcher.find() ? matcher.group(1) : "";
            data.add(new ConnectionEntry(nameSpace, txtConn.getText()));
        }
    }

    private void removeRow() {
        ConnectionEntry selected = dlgTableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            data.remove(selected);
        }
    }

    private void testConnection(ActionEvent event) {
        dialogPane.lookupButton(btnTest).setDisable(true);
        ConnectionEntry selected = dlgTableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            try {
                ServiceBusAdministrationClient adminClient = new ServiceBusAdministrationClientBuilder()
                        .connectionString(selected.connectionString())
                        .buildClient();
                var nsProps = adminClient.getNamespaceProperties();
                String info = String.format("Name: %s%n" +
                                "Created Time: %s%n" +
                                "Modified Time: %s%n" +
                                "Messaging SKU: %s%n" +
                                "Messaging Units: %d%n" +
                                "Type: %s%n",
                        nsProps.getName(),
                        nsProps.getCreatedTime(),
                        nsProps.getModifiedTime(),
                        nsProps.getMessagingSku(),
                        nsProps.getMessagingUnits(),
                        nsProps.getNamespaceType()
                );
                showInfo("Azure Service Bus Connectivity", "✅ Successfully connected to Service Bus", info);

            } catch (Exception e) {
                showError("Azure Service Bus Connectivity", "❌ Unable to connect to Service Bus", e.getMessage());
                System.err.println("❌ Connection failed: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            showError("Azure Service Bus Connectivity", "❌ Unable to connect to Service Bus", "Select a connection first");
        }
        event.consume();
        dialogPane.lookupButton(btnTest).setDisable(false);

    }


    private void connectAction() {
        ConnectionEntry selected = dlgTableView.getSelectionModel().getSelectedItem();
        StopWatch sw = StopWatch.createStarted();
        ApplicationContext context = ApplicationContext.getInstance();
        if (selected != null) {
            System.out.println("Connecting to: " + selected.nameSpace() + " -> " + selected.connectionString());
            if (context.currentManager() != null) context.currentManager().closeAllReceivers();
            context.registerManager(selected.connectionString());
            context.getMainUIController().loadTreeView();
        }
        LogUtils.log(format("Completed Connect Action in %s  Seconds", formatDuration(sw.getDuration().toMillis(), "ss.SSS")));
    }

    private void saveToProperties() {
        try {
            Path configDir = Paths.get(System.getProperty("user.home"), CONFIG_DIR);
            Files.createDirectories(configDir);
            Path propertiesPath = configDir.resolve(PROPERTIES_FILE);
            Properties properties = new Properties();

            for (ConnectionEntry entry : data) {
                if (entry.nameSpace() != null && !entry.nameSpace().trim().isEmpty()) {
                    properties.setProperty(entry.nameSpace(), entry.connectionString() != null ? entry.connectionString() : "");
                }
            }
            try (OutputStream output = new FileOutputStream(propertiesPath.toFile())) {
                properties.store(output, "Connection Strings");
            }
        } catch (IOException e) {
            showError("Error Saving", "❌ Error saving properties", e.getMessage());
        }
    }

    private void loadFromProperties() {
        try {
            Path configDir = Paths.get(System.getProperty("user.home"), CONFIG_DIR);
            Path propertiesPath = configDir.resolve(PROPERTIES_FILE);

            if (Files.exists(propertiesPath)) {
                Properties properties = new Properties();
                try (InputStream input = new FileInputStream(propertiesPath.toFile())) {
                    properties.load(input);
                }

                data.clear();
                properties.forEach((key, value) -> {
                    data.add(new ConnectionEntry(key.toString(), value.toString()));
                });
            }
        } catch (IOException e) {
            showError("Error Loading", "❌ Error saviLoadingng properties", e.getMessage());
        }
    }


    public void initialize(DialogPane pane) {
    }

    // Model class as record
    public record ConnectionEntry(String nameSpace, String connectionString) {
    }
}