package com.jork.utils;

import com.osmb.api.ScriptCore;
import com.osmb.api.location.position.types.LocalPosition;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.shape.Polygon;
import com.osmb.api.visual.image.Image;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.*;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TilePickerPanel {

    private static final PixelFormat<IntBuffer> INT_ARGB_PRE_INSTANCE = PixelFormat.getIntArgbPreInstance();
    private static final Map<LocalPosition, Polygon> LOCAL_POSITION_MAP = new HashMap<>();
    private static LocalPosition selectedTile;

    /**
     * Displays a tile picker panel that allows the user to select a tile on the screen.
     * The selected tile is highlighted in green, and the user can confirm their selection.
     * The method returns the world position of the selected tile.
     *
     * WARNING: This method should only be called when we have established our position in the world.
     *
     * @param core the ScriptCore instance to interact with the scene environment
     * @return the world position of the selected tile
     */
    public static WorldPosition show(ScriptCore core) {
        core.getStageController().show(getScene(core), "Tile Picker", false);
        return selectedTile.toWorldPosition(core);
    }

    private static Scene getScene(ScriptCore core) {
        VBox vBox = new VBox();
        vBox.setStyle("-fx-alignment: center; -fx-spacing: 10px; -fx-background-color: #3a3636");

        Image screenImage = getScreenImage(core);

        // Set up the ImageView with a writable image
        ImageView imageView = new ImageView();
        WritableImage writableImage = new WritableImage(screenImage.getWidth(), screenImage.getHeight());
        writableImage.getPixelWriter().setPixels(0, 0, screenImage.getWidth(), screenImage.getHeight(), INT_ARGB_PRE_INSTANCE, screenImage.getPixels(), 0, screenImage.getWidth());
        imageView.setImage(writableImage);
        imageView.setOnMouseClicked(event -> {
            int mouseX = (int) event.getX();
            int mouseY = (int) event.getY();
            // Calculate the tile position based on mouse coordinates
            for (Map.Entry<LocalPosition, Polygon> entry : LOCAL_POSITION_MAP.entrySet()) {
                LocalPosition localPosition = entry.getKey();
                Polygon tilePoly = entry.getValue();
                if (tilePoly.contains(mouseX, mouseY)) {
                    selectedTile = localPosition;
                    break;
                }
            }
            // update the image
            Image updatedImage = getScreenImage(core);
            WritableImage updatedWritable = new WritableImage(updatedImage.getWidth(), updatedImage.getHeight());
            updatedWritable.getPixelWriter().setPixels(0, 0, updatedImage.getWidth(), updatedImage.getHeight(), INT_ARGB_PRE_INSTANCE, updatedImage.getPixels(), 0, updatedImage.getWidth());
            imageView.setImage(updatedWritable);
        });

        Button refreshScreenButton = new Button("Refresh screen");
        refreshScreenButton.setOnAction(actionEvent -> {
            core.pollFramesUntil(() -> true, 0, true, false);
            Image updatedImage = getScreenImage(core);
            WritableImage updatedWritable = new WritableImage(updatedImage.getWidth(), updatedImage.getHeight());
            updatedWritable.getPixelWriter().setPixels(0, 0, updatedImage.getWidth(), updatedImage.getHeight(), INT_ARGB_PRE_INSTANCE, updatedImage.getPixels(), 0, updatedImage.getWidth());
            imageView.setImage(updatedWritable);
        });
        Button confirmButton = new Button("Confirm selection");
        confirmButton.setOnAction(actionEvent -> {
            if (selectedTile == null) {
                return;
            }
            // Close the stage and return the selected tile's world position
            Stage stage = (Stage) confirmButton.getScene().getWindow();
            stage.close();
        });

        HBox bottomRow = new HBox();
        bottomRow.setFillHeight(true);
        confirmButton.setMaxWidth(Double.MAX_VALUE);
        bottomRow.setStyle("-fx-alignment: center-left; -fx-spacing: 10px; -fx-padding: 10");
        bottomRow.getChildren().addAll(refreshScreenButton, new javafx.scene.layout.Region(), confirmButton);
        HBox.setHgrow(bottomRow.getChildren().get(1), javafx.scene.layout.Priority.ALWAYS);

        bottomRow.setStyle("-fx-alignment: center; -fx-spacing: 10px; -fx-padding: 10");
        vBox.getChildren().addAll(imageView, bottomRow);
        Scene scene = new Scene(vBox);
        scene.getStylesheets().add(TilePickerPanel.class.getResource("/style.css").toExternalForm());
        return scene;
    }

    private static Image getScreenImage(ScriptCore core) {
        LOCAL_POSITION_MAP.clear();
        // Get the screen image from the core
        Image screenImage = core.getScreen().getImage();
        // turn into a drawable canvas, make a copy to avoid modifying the original
        com.osmb.api.visual.drawing.Canvas canvas = new com.osmb.api.visual.drawing.Canvas(screenImage.copy());
        int radius = 13;
        LocalPosition position = core.getLocalPosition();
        List<LocalPosition> reachableTiles = core.getWalker().getCollisionManager().findReachableTiles(position, radius);
        for (LocalPosition localPosition : reachableTiles) {
            Polygon tilePoly = core.getSceneProjector().getTilePoly(localPosition);
            if (tilePoly == null) {
                continue; // skip if tile polygon is null
            }
            LOCAL_POSITION_MAP.put(localPosition, tilePoly);
            canvas.drawPolygon(tilePoly, Color.RED.getRGB(), 1);
        }
        // draw selected tile
        if (TilePickerPanel.selectedTile != null) {
            Polygon selectedTilePoly = LOCAL_POSITION_MAP.get(TilePickerPanel.selectedTile);
            if (selectedTilePoly != null) {
                canvas.fillPolygon(selectedTilePoly, Color.GREEN.getRGB(), 0.3);
                canvas.drawPolygon(selectedTilePoly, Color.GREEN.getRGB(), 1);
            }
        }
        return canvas.toImage();
    }
}
