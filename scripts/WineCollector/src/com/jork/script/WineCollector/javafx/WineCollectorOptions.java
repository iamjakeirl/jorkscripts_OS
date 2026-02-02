package com.jork.script.WineCollector.javafx;

import com.jork.script.WineCollector.WineCollector;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * Minimal dark-themed JavaFX configuration view for WineCollector.
 * Provides:
 *  - Auto-stop toggle with wine-count threshold
 *  - Forced GAME-tab monitoring toggle
 */
public class WineCollectorOptions extends VBox {

    private static final String DARK_BG = "#1e1e1e";
    private static final String DARKER_BG = "#161616";
    private static final String ACCENT_COLOR = "#ff8c42";
    private static final String ACCENT_HOVER = "#ffa05c";
    private static final String TEXT_PRIMARY = "#e4e4e4";
    private static final String TEXT_SECONDARY = "#999999";
    private static final String INPUT_BG = "#2a2a2a";
    private static final String BORDER_COLOR = "#333333";

    private final WineCollector script;
    private final boolean defaultForceGameTab;
    private final int defaultAutoStopCount;

    private final CheckBox autoStopCheck;
    private final TextField autoStopInput;
    private final CheckBox forceGameTabCheck;

    public WineCollectorOptions(WineCollector script, boolean defaultForceGameTab, int defaultAutoStopCount) {
        this.script = script;
        this.defaultForceGameTab = defaultForceGameTab;
        this.defaultAutoStopCount = defaultAutoStopCount;

        setPadding(new Insets(0));
        setSpacing(0);
        setAlignment(Pos.TOP_CENTER);
        setStyle("-fx-background-color: " + DARK_BG + ";");
        setFocusTraversable(true);

        Label title = new Label("Wine Collector Options");
        title.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 16px; -fx-font-weight: bold;");
        title.setPadding(new Insets(12, 0, 12, 0));

        VBox header = new VBox(title);
        header.setAlignment(Pos.CENTER);
        header.setStyle("-fx-background-color: " + DARKER_BG + ";");

        VBox content = new VBox(15);
        content.setPadding(new Insets(18));
        content.setAlignment(Pos.TOP_LEFT);

        // Auto-stop section
        Label autoStopLabel = new Label("AUTO STOP");
        autoStopLabel.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 10px; -fx-font-weight: bold;");

        autoStopCheck = new CheckBox("Stop after collecting this many wines");
        autoStopCheck.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 11px;");
        autoStopCheck.setSelected(false);

        autoStopInput = new TextField(Integer.toString(defaultAutoStopCount));
        autoStopInput.setPrefWidth(80);
        autoStopInput.setDisable(true);
        autoStopInput.setStyle(getInputStyle());
        autoStopInput.textProperty().addListener((obs, oldText, newText) -> {
            if (!newText.matches("\\d*")) {
                autoStopInput.setText(newText.replaceAll("[^\\d]", ""));
            }
        });

        autoStopCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            autoStopInput.setDisable(!newVal);
            if (!newVal) {
                autoStopInput.setText(Integer.toString(defaultAutoStopCount));
            }
        });

        Label winesLabel = new Label("wines");
        winesLabel.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 11px;");

        HBox autoStopRow = new HBox(8, autoStopInput, winesLabel);
        autoStopRow.setAlignment(Pos.CENTER_LEFT);
        autoStopRow.setPadding(new Insets(0, 0, 0, 20));

        // Force game tab section
        Label chatSectionLabel = new Label("CHAT MONITORING");
        chatSectionLabel.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 10px; -fx-font-weight: bold;");

        forceGameTabCheck = new CheckBox("Force GAME tab every frame");
        forceGameTabCheck.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 11px;");
        forceGameTabCheck.setSelected(defaultForceGameTab);

        Label chatInfo = new Label("Keeps chat on GAME tab to ensure hop triggers are detected.\n" +
            "Disable if you need to chat on other tabs while the script runs.");
        chatInfo.setWrapText(true);
        chatInfo.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 10px;");
        chatInfo.setPadding(new Insets(0, 0, 0, 20));

        content.getChildren().addAll(
            autoStopLabel,
            autoStopCheck,
            autoStopRow,
            chatSectionLabel,
            forceGameTabCheck,
            chatInfo
        );

        Button startButton = new Button("Start Script");
        startButton.setPrefWidth(130);
        startButton.setStyle(getButtonStyle());
        startButton.setOnMouseEntered(e -> startButton.setStyle(getButtonHoverStyle()));
        startButton.setOnMouseExited(e -> startButton.setStyle(getButtonStyle()));
        startButton.setOnAction(e -> confirmAndClose());
        startButton.setDefaultButton(true);

        Button cancelButton = new Button("Cancel");
        cancelButton.setPrefWidth(90);
        cancelButton.setStyle(getSecondaryButtonStyle());
        cancelButton.setOnAction(e -> closeWindow());

        HBox buttonRow = new HBox(10, startButton, cancelButton);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);
        buttonRow.setPadding(new Insets(12, 15, 12, 15));
        buttonRow.setStyle("-fx-background-color: " + DARKER_BG + ";");

        // Allow ENTER to confirm
        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                confirmAndClose();
            }
        });

        getChildren().addAll(header, content, buttonRow);
    }

    private void confirmAndClose() {
        script.onOptionsConfirmed(
            autoStopCheck.isSelected(),
            getAutoStopCount(),
            forceGameTabCheck.isSelected()
        );
        closeWindow();
    }

    private void closeWindow() {
        if (getScene() != null && getScene().getWindow() != null) {
            getScene().getWindow().hide();
        }
    }

    private int getAutoStopCount() {
        if (!autoStopCheck.isSelected()) {
            return defaultAutoStopCount;
        }

        try {
            int value = Integer.parseInt(autoStopInput.getText());
            return Math.max(1, Math.min(10000, value));
        } catch (NumberFormatException e) {
            return defaultAutoStopCount;
        }
    }

    private String getInputStyle() {
        return "-fx-background-color: " + INPUT_BG + "; "
            + "-fx-text-fill: " + TEXT_PRIMARY + "; "
            + "-fx-border-color: " + BORDER_COLOR + "; "
            + "-fx-border-radius: 3; "
            + "-fx-background-radius: 3; "
            + "-fx-font-size: 11px;";
    }

    private String getButtonStyle() {
        return "-fx-background-color: " + ACCENT_COLOR + "; "
            + "-fx-text-fill: white; "
            + "-fx-font-weight: bold; "
            + "-fx-background-radius: 4;";
    }

    private String getButtonHoverStyle() {
        return "-fx-background-color: " + ACCENT_HOVER + "; "
            + "-fx-text-fill: white; "
            + "-fx-font-weight: bold; "
            + "-fx-background-radius: 4;";
    }

    private String getSecondaryButtonStyle() {
        return "-fx-background-color: transparent; "
            + "-fx-text-fill: " + TEXT_SECONDARY + "; "
            + "-fx-border-color: " + BORDER_COLOR + "; "
            + "-fx-border-radius: 4;";
    }
}
