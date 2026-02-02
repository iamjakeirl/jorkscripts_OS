package com.jork.script.Ectofuntus.ui;

import com.jork.script.Ectofuntus.Ectofuntus;
import com.jork.script.Ectofuntus.config.BankLocation;
import com.jork.script.Ectofuntus.config.BoneType;
import com.jork.script.Ectofuntus.config.EctoConfig;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;

/**
 * Modern dark-themed JavaFX options window for the Ectofuntus script.
 * Provides configuration options for bone type, banking method, and XP failsafe.
 *
 * @author jork
 */
public class ScriptOptions extends VBox {

    /**
     * Callback interface for settings confirmation.
     * Allows decoupling from the specific Ectofuntus class for testing.
     */
    @FunctionalInterface
    public interface SettingsCallback {
        void onSettingsConfirmed(EctoConfig config, Map<String, Object> options);
    }

    // Dark theme color constants (matching JorkHunter)
    private static final String DARK_BG = "#1e1e1e";
    private static final String DARKER_BG = "#161616";
    private static final String ACCENT_COLOR = "#ff8c42";  // Orange
    private static final String ACCENT_HOVER = "#ffa05c";  // Lighter orange on hover
    private static final String TEXT_PRIMARY = "#e4e4e4";
    private static final String TEXT_SECONDARY = "#999999";
    private static final String BORDER_COLOR = "#333333";
    private static final String INPUT_BG = "#2a2a2a";
    private static final String INPUT_FOCUS = "#363636";

    private final ComboBox<String> boneTypeDropdown;
    private final ComboBox<String> bankLocationDropdown;
    private final CheckBox xpFailsafeCheck;
    private final TextField xpFailsafeTimeoutInput;
    private final CheckBox xpFailsafePauseDuringLogoutCheck;
    private final CheckBox debugLoggingCheck;
    private final CheckBox useAllBonesCheck;
    private final Button confirmBtn;
    private final SettingsCallback callback;

    /**
     * Creates options UI with Ectofuntus script as callback.
     */
    public ScriptOptions(Ectofuntus script) {
        this(script::onSettingsConfirmed);
    }

    /**
     * Creates options UI with custom callback.
     * Useful for testing or alternative integrations.
     */
    public ScriptOptions(SettingsCallback callback) {
        this.callback = callback;
        setSpacing(0);
        setAlignment(Pos.TOP_CENTER);
        setPadding(new Insets(0));
        setMinWidth(360);
        setPrefWidth(380);

        // Apply dark background to main container
        setStyle("-fx-background-color: " + DARK_BG + ";");

        // ── Header Section ──────────────────────────────────────────
        Label titleLabel = new Label("Ectofuntus Configuration");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRIMARY + ";");
        titleLabel.setPadding(new Insets(12, 0, 12, 0));

        VBox headerBox = new VBox(titleLabel);
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setStyle("-fx-background-color: " + DARKER_BG + ";");

        // ── Main Content Container ──────────────────────────────────
        VBox contentBox = new VBox(12);
        contentBox.setPadding(new Insets(15));
        contentBox.setAlignment(Pos.TOP_LEFT);

        // ── Bone Type Section ──────────────────────────────────────
        Label boneSectionLabel = new Label("BONE SELECTION");
        boneSectionLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_SECONDARY + ";");

        Label boneLbl = new Label("Bone Type:");
        boneLbl.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 11px;");
        boneLbl.setMinWidth(80);

        boneTypeDropdown = new ComboBox<>();
        boneTypeDropdown.setPrefWidth(200);
        boneTypeDropdown.setStyle(getComboBoxStyle());
        styleComboBox(boneTypeDropdown);

        // Add all bone types
        for (BoneType type : BoneType.values()) {
            boneTypeDropdown.getItems().add(type.getDisplayName());
        }
        boneTypeDropdown.getSelectionModel().select(BoneType.DRAGON_BONES.getDisplayName());

        HBox boneRow = new HBox(10, boneLbl, boneTypeDropdown);
        boneRow.setAlignment(Pos.CENTER_LEFT);

        VBox boneSection = new VBox(5, boneSectionLabel, boneRow);
        boneSection.setPadding(new Insets(0, 0, 10, 0));

        // ── Banking Method Section ─────────────────────────────────
        Label bankSectionLabel = new Label("BANKING METHOD");
        bankSectionLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_SECONDARY + ";");

        Label bankLbl = new Label("Teleport:");
        bankLbl.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 11px;");
        bankLbl.setMinWidth(80);

        bankLocationDropdown = new ComboBox<>();
        bankLocationDropdown.setPrefWidth(200);
        bankLocationDropdown.setStyle(getComboBoxStyle());
        styleComboBox(bankLocationDropdown);

        // Add banking methods
        for (BankLocation location : BankLocation.values()) {
            bankLocationDropdown.getItems().add(location.getDisplayName());
        }
        bankLocationDropdown.getSelectionModel().select(BankLocation.VARROCK.getDisplayName());

        HBox bankRow = new HBox(10, bankLbl, bankLocationDropdown);
        bankRow.setAlignment(Pos.CENTER_LEFT);

        VBox bankSection = new VBox(5, bankSectionLabel, bankRow);
        bankSection.setPadding(new Insets(0, 0, 10, 0));

        // ── Advanced Options Section ───────────────────────────────
        Label advancedSectionLabel = new Label("ADVANCED OPTIONS");
        advancedSectionLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_SECONDARY + ";");

        // Debug logging toggle
        debugLoggingCheck = new CheckBox("Enable debug logging");
        debugLoggingCheck.setStyle(getCheckBoxStyle());
        debugLoggingCheck.setSelected(false);

        // Mixed bones toggle
        useAllBonesCheck = new CheckBox("Use all bone types in tab");
        useAllBonesCheck.setStyle(getCheckBoxStyle());
        useAllBonesCheck.setSelected(false);  // Default: single bone type mode

        Label useAllBonesInfo = new Label("Withdraws and processes any available bone types");
        useAllBonesInfo.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 9px;");
        useAllBonesInfo.setWrapText(true);
        useAllBonesInfo.setPadding(new Insets(0, 0, 0, 20));

        VBox advancedSection = new VBox(8, advancedSectionLabel, debugLoggingCheck, useAllBonesCheck, useAllBonesInfo);
        advancedSection.setPadding(new Insets(0, 0, 10, 0));

        // ── XP Failsafe Settings ────────────────────────────────────
        Label failsafeSectionLabel = new Label("FAILSAFE SETTINGS");
        failsafeSectionLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_SECONDARY + ";");

        xpFailsafeCheck = new CheckBox("Stop script if no XP gained for:");
        xpFailsafeCheck.setStyle(getCheckBoxStyle());
        xpFailsafeCheck.setSelected(true); // Default enabled for safety

        xpFailsafeTimeoutInput = new TextField("5");
        xpFailsafeTimeoutInput.setPromptText("1-60");
        xpFailsafeTimeoutInput.setPrefWidth(45);
        xpFailsafeTimeoutInput.setStyle(getTextFieldStyle());

        Label minutesLabel = new Label("minutes");
        minutesLabel.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 10px;");

        // Create pause during logout checkbox
        xpFailsafePauseDuringLogoutCheck = new CheckBox("Pause timer during breaks/hops");
        xpFailsafePauseDuringLogoutCheck.setStyle(getCheckBoxStyle());
        xpFailsafePauseDuringLogoutCheck.setSelected(true); // Default enabled
        xpFailsafePauseDuringLogoutCheck.setDisable(!xpFailsafeCheck.isSelected());
        xpFailsafePauseDuringLogoutCheck.setPadding(new Insets(0, 0, 0, 20));

        // Enable/disable controls based on checkbox
        xpFailsafeCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            xpFailsafeTimeoutInput.setDisable(!newVal);
            xpFailsafePauseDuringLogoutCheck.setDisable(!newVal);
            if (!newVal) {
                xpFailsafeTimeoutInput.setText("5");
                xpFailsafePauseDuringLogoutCheck.setSelected(true);
            }
        });

        HBox failsafeBox = new HBox(5, xpFailsafeCheck, xpFailsafeTimeoutInput, minutesLabel);
        failsafeBox.setAlignment(Pos.CENTER_LEFT);

        Label failsafeInfo = new Label("Stops if no Prayer XP is gained within the specified time");
        failsafeInfo.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 9px;");
        failsafeInfo.setWrapText(true);
        failsafeInfo.setPadding(new Insets(0, 0, 0, 20));

        Label pauseInfo = new Label("Prevents false triggers when logged out");
        pauseInfo.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 9px;");
        pauseInfo.setWrapText(true);
        pauseInfo.setPadding(new Insets(0, 0, 0, 40));

        VBox failsafeSection = new VBox(5, failsafeSectionLabel, failsafeBox, failsafeInfo,
                xpFailsafePauseDuringLogoutCheck, pauseInfo);
        failsafeSection.setPadding(new Insets(8, 0, 0, 0));

        // ── Action Button Section ──────────────────────────────────
        confirmBtn = new Button("Start Training");
        confirmBtn.setPrefWidth(120);
        confirmBtn.setPrefHeight(32);
        confirmBtn.setStyle(getButtonStyle());

        // Add hover effect
        confirmBtn.setOnMouseEntered(e -> confirmBtn.setStyle(getButtonHoverStyle()));
        confirmBtn.setOnMouseExited(e -> confirmBtn.setStyle(getButtonStyle()));

        confirmBtn.setOnAction(e -> {
            ((Stage) getScene().getWindow()).close();

            // Build configuration
            BoneType selectedBone = BoneType.fromDisplayName(boneTypeDropdown.getValue());
            BankLocation selectedBank = BankLocation.fromDisplayName(bankLocationDropdown.getValue());

            // Build options map
            Map<String, Object> options = new HashMap<>();
            options.put("xpFailsafeEnabled", xpFailsafeCheck.isSelected());
            options.put("xpFailsafePauseDuringLogout", xpFailsafePauseDuringLogoutCheck.isSelected());

            try {
                int timeout = Integer.parseInt(xpFailsafeTimeoutInput.getText());
                timeout = Math.max(1, Math.min(60, timeout)); // Clamp between 1-60 minutes
                options.put("xpFailsafeTimeout", timeout);
            } catch (NumberFormatException ex) {
                options.put("xpFailsafeTimeout", 5); // Default to 5 minutes
            }

            // Build config object
            EctoConfig config = new EctoConfig(
                selectedBone,
                selectedBank,
                xpFailsafeCheck.isSelected(),
                (Integer) options.get("xpFailsafeTimeout"),
                xpFailsafePauseDuringLogoutCheck.isSelected(),
                debugLoggingCheck.isSelected(),
                useAllBonesCheck.isSelected()
            );

            // Notify via callback
            callback.onSettingsConfirmed(config, options);
        });

        // Add all sections to content box
        contentBox.getChildren().addAll(
            boneSection,
            bankSection,
            advancedSection,
            failsafeSection
        );

        // Button container
        VBox buttonContainer = new VBox(confirmBtn);
        buttonContainer.setAlignment(Pos.CENTER);
        buttonContainer.setPadding(new Insets(12, 15, 12, 15));
        buttonContainer.setStyle("-fx-background-color: " + DARKER_BG + ";");

        // Add all to main container
        getChildren().addAll(headerBox, contentBox, buttonContainer);
    }

    /**
     * Gets the CSS style for combo boxes
     */
    private String getComboBoxStyle() {
        return "-fx-background-color: " + INPUT_BG + "; " +
               "-fx-text-fill: " + TEXT_PRIMARY + "; " +
               "-fx-border-color: " + BORDER_COLOR + "; " +
               "-fx-border-radius: 3; " +
               "-fx-background-radius: 3; " +
               "-fx-font-size: 10px; " +
               "-fx-control-inner-background: " + INPUT_BG + "; " +
               "-fx-control-inner-background-alt: " + INPUT_FOCUS + "; " +
               "-fx-selection-bar: " + ACCENT_COLOR + "; " +
               "-fx-selection-bar-text: white;";
    }

    /**
     * Gets the CSS style for text fields
     */
    private String getTextFieldStyle() {
        return "-fx-background-color: " + INPUT_BG + "; " +
               "-fx-text-fill: " + TEXT_PRIMARY + "; " +
               "-fx-border-color: " + BORDER_COLOR + "; " +
               "-fx-border-radius: 3; " +
               "-fx-background-radius: 3; " +
               "-fx-font-size: 10px; " +
               "-fx-prompt-text-fill: " + TEXT_SECONDARY + ";";
    }

    /**
     * Gets the CSS style for checkboxes
     */
    private String getCheckBoxStyle() {
        return "-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 10px;";
    }

    /**
     * Gets the CSS style for the primary button
     */
    private String getButtonStyle() {
        return "-fx-background-color: " + ACCENT_COLOR + "; " +
               "-fx-text-fill: white; " +
               "-fx-font-size: 12px; " +
               "-fx-font-weight: bold; " +
               "-fx-background-radius: 4; " +
               "-fx-cursor: hand;";
    }

    /**
     * Gets the CSS style for the primary button on hover
     */
    private String getButtonHoverStyle() {
        return "-fx-background-color: " + ACCENT_HOVER + "; " +
               "-fx-text-fill: white; " +
               "-fx-font-size: 12px; " +
               "-fx-font-weight: bold; " +
               "-fx-background-radius: 4; " +
               "-fx-cursor: hand;";
    }

    /**
     * Applies custom styling to ComboBox cells for better dark theme support
     */
    private <T> void styleComboBox(ComboBox<T> comboBox) {
        // Style the button cell (displayed item)
        comboBox.setButtonCell(new javafx.scene.control.ListCell<T>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.toString());
                    setStyle("-fx-text-fill: " + TEXT_PRIMARY + ";");
                }
            }
        });

        // Style the dropdown list cells
        comboBox.setCellFactory(listView -> new javafx.scene.control.ListCell<T>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item.toString());
                    setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; " +
                            "-fx-background-color: " + INPUT_BG + ";");

                    // Hover effect
                    setOnMouseEntered(e -> setStyle("-fx-text-fill: white; " +
                            "-fx-background-color: " + ACCENT_COLOR + ";"));
                    setOnMouseExited(e -> setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; " +
                            "-fx-background-color: " + INPUT_BG + ";"));
                }
            }
        });
    }

    /**
     * Convenience helper to show the window and block until closed
     */
    public static ScriptOptions showAndWait(Ectofuntus script) {
        ScriptOptions pane = new ScriptOptions(script);
        Scene scene = new Scene(pane);
        scene.setFill(javafx.scene.paint.Color.web(DARK_BG));
        Stage stage = new Stage();
        stage.setTitle("Ectofuntus – Configuration");
        stage.setScene(scene);
        stage.setMinWidth(380);
        stage.setMinHeight(420);
        stage.setWidth(380);
        stage.setHeight(440);
        stage.setResizable(false);  // Prevent resizing for consistent appearance
        stage.showAndWait();
        return pane;
    }
}
