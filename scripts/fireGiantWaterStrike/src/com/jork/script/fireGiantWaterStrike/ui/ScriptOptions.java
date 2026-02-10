package com.jork.script.fireGiantWaterStrike.ui;

import com.jork.script.fireGiantWaterStrike.FireGiantWaterStrike;
import com.jork.script.fireGiantWaterStrike.config.CombatConfig;
import com.jork.script.fireGiantWaterStrike.config.FoodType;
import com.jork.script.fireGiantWaterStrike.config.LootMode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Dark-themed JavaFX options panel for the Fire Giant Water Strike script.
 * Provides configuration for loot mode, food type, XP failsafe, and debug logging.
 */
public class ScriptOptions extends VBox {

    /**
     * Callback interface for settings confirmation.
     * Allows decoupling from the specific script class for testing.
     */
    @FunctionalInterface
    public interface SettingsCallback {
        void onSettingsConfirmed(CombatConfig config);
    }

    // Dark theme color constants
    private static final String DARK_BG = "#1e1e1e";
    private static final String DARKER_BG = "#161616";
    private static final String ACCENT_COLOR = "#ff8c42";
    private static final String ACCENT_HOVER = "#ffa05c";
    private static final String TEXT_PRIMARY = "#e4e4e4";
    private static final String TEXT_SECONDARY = "#999999";
    private static final String BORDER_COLOR = "#333333";
    private static final String INPUT_BG = "#2a2a2a";
    private static final String INPUT_FOCUS = "#363636";

    private final ComboBox<String> lootModeDropdown;
    private final ComboBox<String> foodTypeDropdown;
    private final CheckBox staffCoversAirRuneCheck;
    private final CheckBox staffCoversWaterRuneCheck;
    private final CheckBox staffCoversEarthRuneCheck;
    private final CheckBox staffCoversFireRuneCheck;
    private final CheckBox xpFailsafeCheck;
    private final TextField xpFailsafeTimeoutInput;
    private final CheckBox xpFailsafePauseDuringLogoutCheck;
    private final CheckBox debugLoggingCheck;
    private final Button confirmBtn;
    private final SettingsCallback callback;

    /**
     * Creates options UI with FireGiantWaterStrike script as callback.
     */
    public ScriptOptions(FireGiantWaterStrike script) {
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
        Label titleLabel = new Label("Fire Giant Water Strike");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRIMARY + ";");
        titleLabel.setPadding(new Insets(12, 0, 12, 0));

        VBox headerBox = new VBox(titleLabel);
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setStyle("-fx-background-color: " + DARKER_BG + ";");

        // ── Main Content Container ──────────────────────────────────
        VBox contentBox = new VBox(12);
        contentBox.setPadding(new Insets(15));
        contentBox.setAlignment(Pos.TOP_LEFT);

        // ── Combat Settings Section ─────────────────────────────────
        Label combatSectionLabel = new Label("COMBAT SETTINGS");
        combatSectionLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_SECONDARY + ";");

        // Loot Mode dropdown
        Label lootLbl = new Label("Loot Mode:");
        lootLbl.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 11px;");
        lootLbl.setMinWidth(80);

        lootModeDropdown = new ComboBox<>();
        lootModeDropdown.setPrefWidth(200);
        lootModeDropdown.setStyle(getComboBoxStyle());
        styleComboBox(lootModeDropdown);

        for (LootMode mode : LootMode.values()) {
            lootModeDropdown.getItems().add(mode.getDisplayName());
        }
        lootModeDropdown.getSelectionModel().select(LootMode.TELEGRAB.getDisplayName());

        HBox lootRow = new HBox(10, lootLbl, lootModeDropdown);
        lootRow.setAlignment(Pos.CENTER_LEFT);

        // Food Type dropdown
        Label foodLbl = new Label("Food Type:");
        foodLbl.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 11px;");
        foodLbl.setMinWidth(80);

        foodTypeDropdown = new ComboBox<>();
        foodTypeDropdown.setPrefWidth(200);
        foodTypeDropdown.setStyle(getComboBoxStyle());
        styleComboBox(foodTypeDropdown);

        for (FoodType type : FoodType.values()) {
            foodTypeDropdown.getItems().add(type.getDisplayName());
        }
        foodTypeDropdown.getSelectionModel().select(FoodType.LOBSTER.getDisplayName());

        HBox foodRow = new HBox(10, foodLbl, foodTypeDropdown);
        foodRow.setAlignment(Pos.CENTER_LEFT);

        Label staffRunesLabel = new Label("Staff provides elemental runes:");
        staffRunesLabel.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 11px;");
        staffRunesLabel.setPadding(new Insets(4, 0, 0, 0));

        staffCoversAirRuneCheck = new CheckBox("Air");
        staffCoversAirRuneCheck.setStyle(getCheckBoxStyle());
        staffCoversAirRuneCheck.setSelected(false);

        staffCoversWaterRuneCheck = new CheckBox("Water");
        staffCoversWaterRuneCheck.setStyle(getCheckBoxStyle());
        staffCoversWaterRuneCheck.setSelected(false);

        staffCoversEarthRuneCheck = new CheckBox("Earth");
        staffCoversEarthRuneCheck.setStyle(getCheckBoxStyle());
        staffCoversEarthRuneCheck.setSelected(false);

        staffCoversFireRuneCheck = new CheckBox("Fire");
        staffCoversFireRuneCheck.setStyle(getCheckBoxStyle());
        staffCoversFireRuneCheck.setSelected(false);

        HBox staffRuneRow = new HBox(
            10,
            staffCoversAirRuneCheck,
            staffCoversWaterRuneCheck,
            staffCoversEarthRuneCheck,
            staffCoversFireRuneCheck
        );
        staffRuneRow.setAlignment(Pos.CENTER_LEFT);

        Label staffRuneInfo = new Label("Checked runes are excluded from inventory rune checks");
        staffRuneInfo.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 9px;");
        staffRuneInfo.setWrapText(true);
        staffRuneInfo.setPadding(new Insets(0, 0, 0, 20));

        VBox combatSection = new VBox(5, combatSectionLabel, lootRow, foodRow, staffRunesLabel, staffRuneRow, staffRuneInfo);
        combatSection.setPadding(new Insets(0, 0, 10, 0));

        // ── Advanced Options Section ────────────────────────────────
        Label advancedSectionLabel = new Label("ADVANCED OPTIONS");
        advancedSectionLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_SECONDARY + ";");

        debugLoggingCheck = new CheckBox("Enable debug logging");
        debugLoggingCheck.setStyle(getCheckBoxStyle());
        debugLoggingCheck.setSelected(false);

        VBox advancedSection = new VBox(8, advancedSectionLabel, debugLoggingCheck);
        advancedSection.setPadding(new Insets(0, 0, 10, 0));

        // ── Failsafe Settings Section ───────────────────────────────
        Label failsafeSectionLabel = new Label("FAILSAFE SETTINGS");
        failsafeSectionLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_SECONDARY + ";");

        xpFailsafeCheck = new CheckBox("Stop script if no XP gained for:");
        xpFailsafeCheck.setStyle(getCheckBoxStyle());
        xpFailsafeCheck.setSelected(true);

        xpFailsafeTimeoutInput = new TextField("5");
        xpFailsafeTimeoutInput.setPromptText("1-60");
        xpFailsafeTimeoutInput.setPrefWidth(45);
        xpFailsafeTimeoutInput.setStyle(getTextFieldStyle());

        Label minutesLabel = new Label("minutes");
        minutesLabel.setStyle("-fx-text-fill: " + TEXT_SECONDARY + "; -fx-font-size: 10px;");

        xpFailsafePauseDuringLogoutCheck = new CheckBox("Pause timer during breaks/hops");
        xpFailsafePauseDuringLogoutCheck.setStyle(getCheckBoxStyle());
        xpFailsafePauseDuringLogoutCheck.setSelected(true);
        xpFailsafePauseDuringLogoutCheck.setDisable(!xpFailsafeCheck.isSelected());
        xpFailsafePauseDuringLogoutCheck.setPadding(new Insets(0, 0, 0, 20));

        // Enable/disable controls based on failsafe checkbox
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

        Label failsafeInfo = new Label("Stops if no Magic XP is gained within the specified time");
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

        // ── Action Button Section ───────────────────────────────────
        confirmBtn = new Button("Start Script");
        confirmBtn.setPrefWidth(120);
        confirmBtn.setPrefHeight(32);
        confirmBtn.setStyle(getButtonStyle());

        confirmBtn.setOnMouseEntered(e -> confirmBtn.setStyle(getButtonHoverStyle()));
        confirmBtn.setOnMouseExited(e -> confirmBtn.setStyle(getButtonStyle()));

        confirmBtn.setOnAction(e -> {
            ((Stage) getScene().getWindow()).close();

            // Build configuration from UI values
            LootMode selectedLoot = LootMode.fromDisplayName(lootModeDropdown.getValue());
            FoodType selectedFood = FoodType.fromDisplayName(foodTypeDropdown.getValue());

            int timeout = 5;
            try {
                timeout = Integer.parseInt(xpFailsafeTimeoutInput.getText());
                timeout = Math.max(1, Math.min(60, timeout));
            } catch (NumberFormatException ex) {
                timeout = 5;
            }

            CombatConfig config = new CombatConfig(
                selectedLoot,
                selectedFood,
                staffCoversAirRuneCheck.isSelected(),
                staffCoversWaterRuneCheck.isSelected(),
                staffCoversEarthRuneCheck.isSelected(),
                staffCoversFireRuneCheck.isSelected(),
                xpFailsafeCheck.isSelected(),
                timeout,
                xpFailsafePauseDuringLogoutCheck.isSelected(),
                debugLoggingCheck.isSelected()
            );

            callback.onSettingsConfirmed(config);
        });

        // Add all sections to content box
        contentBox.getChildren().addAll(
            combatSection,
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
     * Gets the CSS style for combo boxes.
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
     * Gets the CSS style for text fields.
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
     * Gets the CSS style for checkboxes.
     */
    private String getCheckBoxStyle() {
        return "-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-size: 10px;";
    }

    /**
     * Gets the CSS style for the primary button.
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
     * Gets the CSS style for the primary button on hover.
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
     * Applies custom styling to ComboBox cells for better dark theme support.
     */
    private <T> void styleComboBox(ComboBox<T> comboBox) {
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

                    setOnMouseEntered(e -> setStyle("-fx-text-fill: white; " +
                            "-fx-background-color: " + ACCENT_COLOR + ";"));
                    setOnMouseExited(e -> setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; " +
                            "-fx-background-color: " + INPUT_BG + ";"));
                }
            }
        });
    }
}
