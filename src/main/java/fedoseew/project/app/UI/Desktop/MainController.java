package fedoseew.project.app.UI.Desktop;

import fedoseew.project.app.Configurations.ApplicationConfiguration;
import fedoseew.project.app.Database.DB_TABLES;
import fedoseew.project.app.Database.DatabaseUtils;
import fedoseew.project.app.Database.InsertScriptsFileUtils;
import fedoseew.project.app.Logic.ImageRecognition;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class MainController {
    private final List<List<Map<Node, Boolean>>> cells = new ArrayList<>();

    @FXML
    Label label;

    @FXML
    AnchorPane anchorPane;

    @FXML
    GridPane grid;

    @FXML
    private Button go;

    @FXML
    private Button closeBtn;

    @FXML
    private Slider alphaScroll;

    @FXML
    private Slider bettaScroll;

    @FXML
    private Slider gammaScroll;

    @FXML
    private TextField alphaField;

    @FXML
    private TextField bettaField;

    @FXML
    private TextField gammaField;

    @FXML
    private TextField minMetric;

    @FXML
    void initialize() {
        createGrid();
        connectToDatabase();
        setListenerForGoButton();
        setListenersForSettingScrolls();
        setListenerForCloseButton();
    }

    private void createGrid() {

        grid.setCursor(Cursor.HAND);

        int sizeOfGrid = ApplicationConfiguration.getSizeOfGrid();

        for (int i = 1; i <= sizeOfGrid; i++) {

            List<Map<Node, Boolean>> row = new ArrayList<>();
            cells.add(row);

            for (int j = 1; j <= sizeOfGrid; j++) {

                Node cell = createCell();
                Map<Node, Boolean> map = new HashMap<>();
                map.put(cell, false);
                row.add(map);
                grid.add(cell, j, i);

            }
        }
    }

    private Node createCell() {
        ListView<?> cell = new ListView<>();

        cell.setCursor(Cursor.HAND);
        cell.setStyle("-fx-background-color: white !important; -fx-border-color: black");

        cell.setOnMouseClicked(click -> {

            if (cell.getStyle().contains("-fx-background-color: black")) {

                cell.setStyle("-fx-background-color: white; -fx-border-color: black");
                setValue(cell, false);

            } else {
                cell.setStyle("-fx-background-color: black; -fx-border-color: black");
                setValue(cell, true);

            }
        });

        cell.hoverProperty()
                .addListener((ObservableValue<? extends Boolean> observable,
                              Boolean oldValue, Boolean newValue) -> {

                    if (!cell.getStyle().contains("-fx-background-color: black")) {

                        if (newValue) {

                            cell.setStyle("-fx-background-color: #425c81; -fx-border-color: black");

                        } else {

                            cell.setStyle("-fx-background-color: white; -fx-border-color: black");

                        }
                    }
                });

        return cell;
    }

    private void setValue(Node cell, boolean value) {

        for (List<Map<Node, Boolean>> rows : cells) {

            for (Map<Node, Boolean> map : rows) {

                if (map.containsKey(cell)) {

                    map.replace(cell, value);

                }
            }
        }
    }

    private void connectToDatabase() {
        try {
            Connection connection = DatabaseUtils.createOrGetDatabase().getConnection();
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(ApplicationConfiguration.getPathToCreateScripts()));
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(ApplicationConfiguration.getPathToInsertScripts()));
            Logger.getGlobal().info("Connected to database");
        } catch (SQLException exception) {
            Logger.getGlobal().warning(exception.getLocalizedMessage());
            exception.printStackTrace();
        }
    }

    private void setListenerForGoButton() {
        go.setOnMouseClicked(click -> {
            ImageRecognition imageRecognition = new ImageRecognition();
            double metric = 0.5;
            try {
                metric = Double.parseDouble(minMetric.getText());
            } catch (NumberFormatException ignored) {
            }
            try {
                Map<DB_TABLES, Integer> response = imageRecognition.recognition(parseImageToBinaryCode(), new Object[]{
                        (int) alphaScroll.getValue(),
                        (int) bettaScroll.getValue(),
                        (int) gammaScroll.getValue(),
                        metric
                });
                createResponseNotification(response);
                clearAllCells();

            } catch (SQLException | IOException exception) {
                Logger.getGlobal().warning(exception.getLocalizedMessage());
                exception.printStackTrace();
            }
        });
    }

    private void clearAllCells() {
        cells.forEach(row -> row.forEach(cell -> {
            cell.keySet().forEach(node -> {
                node.setStyle("-fx-background-color: white; -fx-border-color: black");
            });
            cell.entrySet().forEach(entry -> entry.setValue(false));
        }));
    }

    private String parseImageToBinaryCode() {
        StringBuilder stringBuilder = new StringBuilder();
        for (List<Map<Node, Boolean>> rows : cells) {
            for (Map<Node, Boolean> cell : rows) {
                cell.forEach((key, value) -> {
                    if (value.equals(true)) {
                        stringBuilder.append(1);
                    } else {
                        stringBuilder.append(0);
                    }
                });
            }
        }
        return stringBuilder.toString();
    }

    private void createResponseNotification(Map<DB_TABLES, Integer> response) throws IOException, SQLException {
        Dialog<ButtonType> responseAlert = new Dialog<>();
        responseAlert.setTitle("Recognition Image App");

        ButtonType YES = new ButtonType("YES!", ButtonBar.ButtonData.YES);
        ButtonType NO = new ButtonType("NO!", ButtonBar.ButtonData.NO);
        AtomicInteger resultNumber = new AtomicInteger(-1);
        response.forEach((key, value) -> resultNumber.set(value));

        if (resultNumber.get() >= 0) {
            responseAlert.setHeaderText("Recognition completed!");
            responseAlert.setContentText("Successfully! Your number is " + resultNumber + ", right?");
            responseAlert.getDialogPane().getButtonTypes().add(YES);
            responseAlert.getDialogPane().getButtonTypes().add(NO);

        } else {
            responseAlert.setHeaderText("Recognition completed!");
            responseAlert.setContentText("Ooops! Failed to recognize the image :( Try again.");
            responseAlert.getDialogPane().getButtonTypes().add(
                    new ButtonType("Try again", ButtonBar.ButtonData.CANCEL_CLOSE));
        }

        Optional<ButtonType> result = responseAlert.showAndWait();
        if (result.isPresent()) {
            setListenersOnCloseDialogEvent(result.get());
        }
    }

    private void setListenersOnCloseDialogEvent(ButtonType result) throws SQLException, IOException {
        if (!Objects.isNull(result)) {
            if (result.getButtonData().equals(ButtonBar.ButtonData.NO)) {
                AtomicReference<DB_TABLES> db_table = new AtomicReference<>();
                double metric = 0.5;
                try {
                    metric = Double.parseDouble(minMetric.getText());
                } catch (NumberFormatException ignored) {
                }
                new ImageRecognition()
                        .recognition(parseImageToBinaryCode(), new Object[]{
                                (int) alphaScroll.getValue(),
                                (int) bettaScroll.getValue(),
                                (int) gammaScroll.getValue(),
                                metric
                        })
                        .forEach((key, value) -> db_table.set(key));

                int chosenNumber = createInputDialog();
                if (chosenNumber != -3) {
                    if (!(chosenNumber <= 9 && chosenNumber >= 0)) {
                        while (chosenNumber != -1) {
                            chosenNumber = createInputDialog();
                            if (chosenNumber <= 9 && chosenNumber >= 0 || chosenNumber == -3) {
                                break;
                            }
                        }
                    }
                }
                if (chosenNumber == -3) {
                    InsertScriptsFileUtils.deleteSourceFromInsertScriptsFile(parseImageToBinaryCode(), db_table.get(), true);
                    String SQL = "DELETE FROM " + db_table.get()
                            + " WHERE source='" + parseImageToBinaryCode() + "'";
                    DatabaseUtils.createOrGetDatabase().insertQuery(SQL);
                    return;
                }
                if (chosenNumber != -1) {
                    InsertScriptsFileUtils.deleteSourceFromInsertScriptsFile(parseImageToBinaryCode(), db_table.get(), true);
                    DatabaseUtils.createOrGetDatabase().insertQuery("DELETE FROM " + db_table.get()
                            + " WHERE source='" + parseImageToBinaryCode() + "'");
                    DB_TABLES newTable = Arrays
                            .stream(DB_TABLES.values())
                            .collect(Collectors.toList())
                            .get(chosenNumber);
                    InsertScriptsFileUtils
                            .writeInsertScriptsFile(parseImageToBinaryCode(), newTable, true);
                    String SQL = "INSERT INTO " + newTable +
                            " VALUES('" + parseImageToBinaryCode() + "', true);";
                    DatabaseUtils.createOrGetDatabase().insertQuery(SQL);
                }
            }
        }
    }

    private int createInputDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Choose your number");
        dialog.setHeaderText("Enter your number(0-9 or 'no one'):");
        dialog.setContentText("Your number is:");

        AtomicReference<Optional<String>> number = new AtomicReference<>(dialog.showAndWait());
        AtomicInteger result = new AtomicInteger(-1);
        number.get().ifPresent(text -> {
            try {
                if ("no one" .equals(text)) {
                    result.set(-3);
                } else {
                    result.set(Integer.parseInt(text));
                    if (result.get() > 9 || result.get() < 0) {
                        result.set(-2);
                    }
                }

            } catch (NumberFormatException exception) {
                result.set(-2);
            }
        });
        return result.get();
    }

    private void setListenersForSettingScrolls() {
        alphaScroll.valueProperty().addListener((observableValue, number, t1) -> {
            setTextAndStyleForSlider(alphaScroll, alphaField, t1);
        });
        bettaScroll.valueProperty().addListener((observableValue, number, t1) -> {
            setTextAndStyleForSlider(bettaScroll, bettaField, t1);
        });
        gammaScroll.valueProperty().addListener((observableValue, number, t1) -> {
            setTextAndStyleForSlider(gammaScroll, gammaField, t1);
        });
    }

    private void setTextAndStyleForSlider(Slider slider, TextField textField, Number number) {
        textField.setText(String.valueOf(number.intValue()));
        int rgb = 255 - number.intValue();
        String color = "rgb(" + 255 + ", " + rgb + ", " + rgb + ")";
        slider.setStyle("-fx-control-inner-background: " + color);
    }

    private void setListenerForCloseButton() {
        closeBtn.setOnMouseClicked(click -> {
            Stage stage = (Stage) closeBtn.getScene().getWindow();
            stage.close();
            Logger.getGlobal().info("APPLICATION STOPPED...");
        });
    }
}

