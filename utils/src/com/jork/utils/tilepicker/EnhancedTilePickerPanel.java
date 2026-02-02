package com.jork.utils.tilepicker;

import com.osmb.api.ScriptCore;
import com.osmb.api.location.position.types.LocalPosition;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.shape.Polygon;
import com.osmb.api.visual.image.Image;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.Color;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Enhanced tile picker panel that supports multiple tile selection with categories.
 * Backwards compatible with the original TilePickerPanel API.
 */
public class EnhancedTilePickerPanel {

    private static final PixelFormat<IntBuffer> INT_ARGB_PRE_INSTANCE = PixelFormat.getIntArgbPreInstance();
    
    private final ScriptCore core;
    private final SelectionMode selectionMode;
    private final List<TileCategory> categories;
    private final Map<LocalPosition, Polygon> tilePolygons = new HashMap<>();
    private final Map<LocalPosition, String> selectedTiles = new HashMap<>();
    private String lastUsedCategory = null;
    private ImageView imageView;
    private VBox categoryLegend;
    private Label statusLabel;
    private boolean selectionConfirmed = false;
    
    public enum SelectionMode {
        SINGLE,
        MULTIPLE
    }
    
    /**
     * Private constructor - use static factory methods or Builder.
     */
    private EnhancedTilePickerPanel(ScriptCore core, SelectionMode mode, List<TileCategory> categories) {
        this.core = core;
        this.selectionMode = mode;
        this.categories = categories != null ? new ArrayList<>(categories) : new ArrayList<>();
        
        // If single mode with no categories, create a default category
        if (mode == SelectionMode.SINGLE && this.categories.isEmpty()) {
            this.categories.add(new TileCategory("default", "Selected Tile", Color.GREEN));
            this.lastUsedCategory = "default";
        } else if (!this.categories.isEmpty()) {
            this.lastUsedCategory = this.categories.get(0).getId();
        }
    }
    
    /**
     * Backwards compatible single tile selection.
     * Shows a tile picker and returns a single WorldPosition.
     * 
     * @param core ScriptCore instance
     * @return Selected WorldPosition or null if cancelled
     */
    public static WorldPosition show(ScriptCore core) {
        EnhancedTilePickerPanel panel = new EnhancedTilePickerPanel(core, SelectionMode.SINGLE, null);
        Scene scene = panel.createScene();
        
        core.getStageController().show(scene, "Tile Picker", false);
        
        if (!panel.selectedTiles.isEmpty()) {
            LocalPosition localPos = panel.selectedTiles.keySet().iterator().next();
            return localPos.toWorldPosition(core);
        }
        return null;
    }
    
    /**
     * Shows tile picker for multiple selection without categories.
     * 
     * @param core ScriptCore instance
     * @return List of selected WorldPositions
     */
    public static List<WorldPosition> showMultiple(ScriptCore core) {
        List<TileCategory> defaultCategory = Collections.singletonList(
            new TileCategory("default", "Selected Tiles", Color.GREEN)
        );
        
        EnhancedTilePickerPanel panel = new EnhancedTilePickerPanel(core, SelectionMode.MULTIPLE, defaultCategory);
        Scene scene = panel.createScene();
        
        core.getStageController().show(scene, "Tile Picker - Multiple Selection", false);
        
        return panel.selectedTiles.keySet().stream()
            .map(pos -> pos.toWorldPosition(core))
            .collect(Collectors.toList());
    }
    
    /**
     * Shows tile picker with custom categories.
     * 
     * @param core ScriptCore instance
     * @param categories List of categories for tile assignment
     * @return Map of category IDs to their selected positions
     */
    public static Map<String, List<WorldPosition>> showWithCategories(ScriptCore core, List<TileCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            throw new IllegalArgumentException("Categories list cannot be null or empty");
        }
        
        EnhancedTilePickerPanel panel = new EnhancedTilePickerPanel(core, SelectionMode.MULTIPLE, categories);
        Scene scene = panel.createScene();
        
        core.getStageController().show(scene, "Tile Picker - Categorized Selection", false);
        
        Map<String, List<WorldPosition>> result = new HashMap<>();
        for (TileCategory category : categories) {
            result.put(category.getId(), new ArrayList<>());
        }
        
        for (Map.Entry<LocalPosition, String> entry : panel.selectedTiles.entrySet()) {
            WorldPosition worldPos = entry.getKey().toWorldPosition(core);
            result.get(entry.getValue()).add(worldPos);
        }
        
        return result;
    }
    
    /**
     * Builder pattern for complex configurations.
     */
    public static Builder builder(ScriptCore core) {
        return new Builder(core);
    }
    
    /**
     * Builder class for flexible configuration.
     */
    public static class Builder {
        private final ScriptCore core;
        private final List<TileCategory> categories = new ArrayList<>();
        private SelectionMode mode = SelectionMode.MULTIPLE;
        private String windowTitle = "Enhanced Tile Picker";
        
        private Builder(ScriptCore core) {
            this.core = core;
        }
        
        public Builder withCategory(String id, String displayName, Color color) {
            categories.add(new TileCategory(id, displayName, color));
            return this;
        }
        
        public Builder withCategory(String id, String displayName, Color color, int maxSelections) {
            categories.add(new TileCategory(id, displayName, color, maxSelections));
            return this;
        }
        
        public Builder withCategory(TileCategory category) {
            categories.add(category);
            return this;
        }
        
        public Builder withSelectionMode(SelectionMode mode) {
            this.mode = mode;
            return this;
        }
        
        public Builder withTitle(String title) {
            this.windowTitle = title;
            return this;
        }
        
        public Map<String, List<WorldPosition>> show() {
            if (categories.isEmpty()) {
                categories.add(new TileCategory("default", "Selected Tiles", Color.GREEN));
            }
            
            EnhancedTilePickerPanel panel = new EnhancedTilePickerPanel(core, mode, categories);
            Scene scene = panel.createScene();
            
            core.getStageController().show(scene, windowTitle, false);
            
            Map<String, List<WorldPosition>> result = new HashMap<>();
            for (TileCategory category : categories) {
                result.put(category.getId(), new ArrayList<>());
            }
            
            for (Map.Entry<LocalPosition, String> entry : panel.selectedTiles.entrySet()) {
                WorldPosition worldPos = entry.getKey().toWorldPosition(core);
                result.get(entry.getValue()).add(worldPos);
            }
            
            return result;
        }
    }
    
    /**
     * Creates the JavaFX scene for the tile picker.
     */
    private Scene createScene() {
        VBox mainContainer = new VBox();
        mainContainer.setStyle("-fx-alignment: center; -fx-spacing: 10px; -fx-background-color: #3a3636");
        
        // Create the screen image display
        Image screenImage = captureScreenWithOverlay();
        imageView = new ImageView();
        WritableImage writableImage = new WritableImage(screenImage.getWidth(), screenImage.getHeight());
        writableImage.getPixelWriter().setPixels(0, 0, screenImage.getWidth(), screenImage.getHeight(), 
            INT_ARGB_PRE_INSTANCE, screenImage.getPixels(), 0, screenImage.getWidth());
        imageView.setImage(writableImage);
        
        // Set up mouse interaction
        setupMouseInteraction();
        
        // Create category legend if multiple categories exist
        HBox topContainer = new HBox(10);
        topContainer.setPadding(new Insets(10));
        topContainer.setAlignment(Pos.TOP_LEFT);
        
        if (categories.size() > 1 || (categories.size() == 1 && !categories.get(0).getId().equals("default"))) {
            categoryLegend = createCategoryLegend();
            topContainer.getChildren().add(categoryLegend);
        }
        
        // Status label
        statusLabel = new Label(getStatusText());
        statusLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        topContainer.getChildren().addAll(spacer, statusLabel);
        
        // Bottom control buttons
        HBox buttonContainer = createButtonContainer();
        
        // Assemble the main container
        mainContainer.getChildren().addAll(topContainer, imageView, buttonContainer);
        
        Scene scene = new Scene(mainContainer);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        
        // Add keyboard shortcut for Enter key to confirm selection
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                selectionConfirmed = true;
                Stage stage = (Stage) scene.getWindow();
                stage.close();
            }
        });
        
        return scene;
    }
    
    /**
     * Sets up mouse interaction for tile selection.
     */
    private void setupMouseInteraction() {
        ContextMenu contextMenu = createCategoryContextMenu();
        
        imageView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.SECONDARY && categories.size() > 1) {
                // Right-click - show category menu
                LocalPosition clickedTile = getTileAtPosition((int) event.getX(), (int) event.getY());
                if (clickedTile != null) {
                    // Store the clicked tile for the context menu actions
                    contextMenu.setUserData(clickedTile);
                    contextMenu.show(imageView, event.getScreenX(), event.getScreenY());
                }
            } else if (event.getButton() == MouseButton.PRIMARY) {
                // Left-click - select/deselect
                handleLeftClick((int) event.getX(), (int) event.getY());
            }
        });
    }
    
    /**
     * Handles left-click for tile selection/deselection.
     */
    private void handleLeftClick(int mouseX, int mouseY) {
        LocalPosition clickedTile = getTileAtPosition(mouseX, mouseY);
        if (clickedTile == null) return;
        
        if (selectedTiles.containsKey(clickedTile)) {
            // Deselect
            selectedTiles.remove(clickedTile);
        } else {
            // Select
            if (selectionMode == SelectionMode.SINGLE) {
                selectedTiles.clear();
            }
            
            String categoryToUse = (categories.size() == 1)
                ? categories.get(0).getId()
                : lastUsedCategory;
            
            if (categoryToUse != null && canAddToCategory(categoryToUse)) {
                selectedTiles.put(clickedTile, categoryToUse);
            }
        }
        
        updateDisplay();
    }
    
    /**
     * Creates the category context menu for right-click assignment.
     */
    private ContextMenu createCategoryContextMenu() {
        ContextMenu menu = new ContextMenu();
        
        for (TileCategory category : categories) {
            MenuItem item = new MenuItem(category.getDisplayName());
            
            // Add color indicator
            javafx.scene.shape.Rectangle colorRect = new javafx.scene.shape.Rectangle(12, 12);
            colorRect.setFill(javafx.scene.paint.Color.rgb(
                category.getColor().getRed(),
                category.getColor().getGreen(), 
                category.getColor().getBlue()
            ));
            item.setGraphic(colorRect);
            
            item.setOnAction(e -> {
                // Right-click category selection should not select the tile; it only sets active category
                lastUsedCategory = category.getId();
                // Update legend/status to reflect the active category change
                updateDisplay();
            });
            
            menu.getItems().add(item);
        }
        
        if (categories.size() > 1) {
            menu.getItems().add(new SeparatorMenuItem());
            MenuItem removeItem = new MenuItem("Remove Selection");
            removeItem.setOnAction(e -> {
                LocalPosition tile = (LocalPosition) menu.getUserData();
                if (tile != null) {
                    selectedTiles.remove(tile);
                    updateDisplay();
                }
            });
            menu.getItems().add(removeItem);
        }
        
        return menu;
    }
    
    /**
     * Assigns a tile to a specific category.
     */
    private void assignTileToCategory(LocalPosition tile, String categoryId) {
        String current = selectedTiles.get(tile);
        // If already in the target category, just update the active category and refresh
        if (categoryId.equals(current)) {
            lastUsedCategory = categoryId;
            updateDisplay();
            return;
        }

        // Enforce category limits on reassignment: only assign if target can accept more
        if (canAddToCategory(categoryId)) {
            selectedTiles.put(tile, categoryId);
            lastUsedCategory = categoryId;
            updateDisplay();
        }
    }
    
    /**
     * Checks if a category can accept more selections.
     */
    private boolean canAddToCategory(String categoryId) {
        TileCategory category = categories.stream()
            .filter(c -> c.getId().equals(categoryId))
            .findFirst()
            .orElse(null);
            
        if (category == null || !category.hasLimit()) {
            return true;
        }
        
        long currentCount = selectedTiles.values().stream()
            .filter(id -> id.equals(categoryId))
            .count();
            
        return currentCount < category.getMaxSelections();
    }
    
    /**
     * Gets the tile at the specified screen coordinates.
     */
    private LocalPosition getTileAtPosition(int mouseX, int mouseY) {
        for (Map.Entry<LocalPosition, Polygon> entry : tilePolygons.entrySet()) {
            if (entry.getValue().contains(mouseX, mouseY)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * Creates the category legend display.
     */
    private VBox createCategoryLegend() {
        VBox legend = new VBox(5);
        legend.setStyle("-fx-background-color: rgba(0,0,0,0.7); -fx-padding: 10; -fx-background-radius: 5;");
        
        Label title = new Label("Categories:");
        title.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px;");
        legend.getChildren().add(title);
        
        for (TileCategory category : categories) {
            HBox categoryRow = new HBox(5);
            categoryRow.setAlignment(Pos.CENTER_LEFT);
            
            javafx.scene.shape.Rectangle colorRect = new javafx.scene.shape.Rectangle(12, 12);
            colorRect.setFill(javafx.scene.paint.Color.rgb(
                category.getColor().getRed(),
                category.getColor().getGreen(),
                category.getColor().getBlue()
            ));
            
            long count = selectedTiles.values().stream()
                .filter(id -> id.equals(category.getId()))
                .count();
            
            String labelText = category.getDisplayName();
            if (category.hasLimit()) {
                labelText += String.format(" (%d/%d)", count, category.getMaxSelections());
            } else {
                labelText += String.format(" (%d)", count);
            }
            
            Label categoryLabel = new Label(labelText);
            categoryLabel.setStyle("-fx-text-fill: white; -fx-font-size: 11px;");
            
            categoryRow.getChildren().addAll(colorRect, categoryLabel);
            legend.getChildren().add(categoryRow);
        }
        
        return legend;
    }
    
    /**
     * Creates the bottom button container.
     */
    private HBox createButtonContainer() {
        HBox container = new HBox(10);
        container.setPadding(new Insets(10));
        container.setAlignment(Pos.CENTER);
        container.setStyle("-fx-background-color: #2a2626;");
        
        Button refreshBtn = new Button("Refresh Screen");
        refreshBtn.setOnAction(e -> {
            core.pollFramesUntil(() -> true, 0, true, false);
            updateDisplay();
        });
        
        Button clearAllBtn = new Button("Clear All");
        clearAllBtn.setOnAction(e -> {
            selectedTiles.clear();
            updateDisplay();
        });
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        Button confirmBtn = new Button("Confirm Selection");
        confirmBtn.setStyle("-fx-font-weight: bold;");
        confirmBtn.setOnAction(e -> {
            selectionConfirmed = true;
            Stage stage = (Stage) confirmBtn.getScene().getWindow();
            stage.close();
        });
        
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setOnAction(e -> {
            selectedTiles.clear();
            Stage stage = (Stage) cancelBtn.getScene().getWindow();
            stage.close();
        });
        
        container.getChildren().addAll(refreshBtn, clearAllBtn, spacer, cancelBtn, confirmBtn);
        
        return container;
    }
    
    /**
     * Updates the display with current selections.
     */
    private void updateDisplay() {
        Image updatedImage = captureScreenWithOverlay();
        WritableImage writableImage = new WritableImage(updatedImage.getWidth(), updatedImage.getHeight());
        writableImage.getPixelWriter().setPixels(0, 0, updatedImage.getWidth(), updatedImage.getHeight(),
            INT_ARGB_PRE_INSTANCE, updatedImage.getPixels(), 0, updatedImage.getWidth());
        imageView.setImage(writableImage);
        
        if (categoryLegend != null) {
            categoryLegend.getChildren().clear();
            VBox newLegend = createCategoryLegend();
            categoryLegend.getChildren().addAll(newLegend.getChildren());
        }
        
        if (statusLabel != null) {
            statusLabel.setText(getStatusText());
        }
    }
    
    /**
     * Gets the current status text.
     */
    private String getStatusText() {
        if (selectionMode == SelectionMode.SINGLE) {
            return selectedTiles.isEmpty() ? "Click to select a tile" : "Tile selected";
        } else {
            int total = selectedTiles.size();
            if (total == 0) {
                return "Click to select tiles";
            } else {
                return String.format("%d tile%s selected", total, total == 1 ? "" : "s");
            }
        }
    }
    
    /**
     * Captures the screen and draws tile overlays.
     */
    private Image captureScreenWithOverlay() {
        tilePolygons.clear();
        
        // Get the screen image
        Image screenImage = core.getScreen().getImage();
        com.osmb.api.visual.drawing.Canvas canvas = new com.osmb.api.visual.drawing.Canvas(screenImage.copy());
        
        // Get reachable tiles
        int radius = 13;
        LocalPosition position = core.getLocalPosition();
        List<LocalPosition> reachableTiles = core.getWalker().getCollisionManager().findReachableTiles(position, radius);
        
        // Draw reachable tiles
        for (LocalPosition localPos : reachableTiles) {
            Polygon tilePoly = core.getSceneProjector().getTilePoly(localPos);
            if (tilePoly == null) continue;
            
            tilePolygons.put(localPos, tilePoly);
            
            // Check if this tile is selected
            if (selectedTiles.containsKey(localPos)) {
                String categoryId = selectedTiles.get(localPos);
                TileCategory category = categories.stream()
                    .filter(c -> c.getId().equals(categoryId))
                    .findFirst()
                    .orElse(null);
                    
                if (category != null) {
                    Color color = category.getColor();
                    canvas.fillPolygon(tilePoly, color.getRGB(), 0.3);
                    canvas.drawPolygon(tilePoly, color.getRGB(), 2);
                }
            } else {
                // Draw unselected reachable tiles in light red
                canvas.drawPolygon(tilePoly, Color.RED.getRGB(), 1);
            }
        }
        
        return canvas.toImage();
    }
}
