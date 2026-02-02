package com.jork.script.WineCollector;

import com.osmb.api.script.ScriptDefinition;
import com.osmb.api.script.SkillCategory;
import com.osmb.api.visual.drawing.Canvas;
import com.osmb.api.ui.chatbox.ChatboxFilterTab;
import com.osmb.api.shape.Polygon;
import com.osmb.api.shape.Rectangle;
import com.osmb.api.visual.SearchablePixel;
import com.osmb.api.visual.color.ColorModel;
import com.osmb.api.visual.color.tolerance.impl.SingleThresholdComparator;
import com.jork.utils.metrics.AbstractMetricsScript;
import com.jork.utils.metrics.core.MetricType;
import com.jork.utils.ScriptLogger;
import com.jork.utils.chat.ChatBoxListener;
import com.jork.script.WineCollector.tasks.*;
import com.jork.script.WineCollector.config.WineConfig;
import com.osmb.api.item.ItemGroupResult;
import com.osmb.api.utils.RandomUtils;

import java.awt.Point;
import java.util.Set;

import javafx.scene.Scene;

import com.jork.script.WineCollector.javafx.WineCollectorOptions;

@ScriptDefinition(
    name = "Wine Collector",
    author = "jork",
    version = 1.0,
    description = "Collects Eclipse Red wines in Varlamore Hunting Guild",
    skillCategory = SkillCategory.OTHER
)
public class WineCollector extends AbstractMetricsScript {

    private TaskManager taskManager;
    private ChatBoxListener chatListener;
    private int wineCount = 0;
    private boolean shouldBank = false;
    private volatile boolean chatHopTriggered = false;
    private volatile boolean settingsConfirmed = false;
    private boolean initialised = false;
    private boolean autoStopEnabled = false;
    private int autoStopLimit = DEFAULT_AUTO_STOP_COUNT;
    private boolean forceGameTabMonitoring = true;

    private static final int DEFAULT_AUTO_STOP_COUNT = 112;

    public WineCollector(Object scriptCore) {
        super(scriptCore);
    }

    @Override
    protected void onMetricsStart() {
        ScriptLogger.startup(this, "1.0", "jork", "Wine Collector");

        WineCollectorOptions options = new WineCollectorOptions(
            this,
            forceGameTabMonitoring,
            DEFAULT_AUTO_STOP_COUNT
        );
        Scene scene = new Scene(options);
        getStageController().show(scene, "Wine Collector – Options", false);

        if (scene.getWindow() != null) {
            scene.getWindow().setOnHidden(e -> {
                if (!settingsConfirmed) {
                    onOptionsConfirmed(false, DEFAULT_AUTO_STOP_COUNT, true);
                }
            });
        }
    }

    public void onOptionsConfirmed(boolean autoStop, int autoStopCount, boolean forceGameTab) {
        this.autoStopEnabled = autoStop;
        this.autoStopLimit = Math.max(1, autoStopCount);
        this.forceGameTabMonitoring = forceGameTab;
        this.settingsConfirmed = true;

        if (autoStopEnabled) {
            ScriptLogger.info(this, "Auto-stop enabled after " + autoStopLimit + " wines.");
        } else {
            ScriptLogger.info(this, "Auto-stop disabled.");
        }

        ScriptLogger.info(this, "Force GAME tab monitoring " + (forceGameTab ? "ENABLED" : "DISABLED"));
    }

    private void initialiseIfReady() {
        if (initialised || !settingsConfirmed) {
            return;
        }

        // Check inventory on startup to set initial shouldBank flag
        ItemGroupResult startupInventory = getWidgetManager().getInventory().search(Set.of());
        if (startupInventory != null && startupInventory.getOccupiedSlotCount() >= WineConfig.INVENTORY_SIZE) {
            shouldBank = true;
            ScriptLogger.info(this, "Inventory full on startup - will navigate to bank");
        } else {
            shouldBank = false;
            ScriptLogger.info(this, "Inventory has space on startup - will navigate to collection area");
        }

        taskManager = new TaskManager(this);

        NavigateTask navigateTask = new NavigateTask(this);
        BankTask bankTask = new BankTask(this);
        CollectTask collectTask = new CollectTask(this);
        taskManager.addTasks(navigateTask, bankTask, collectTask);

        // Initialize chatbox listener for hop triggers
        // Register multiple patterns for OCR error tolerance
        chatListener = new ChatBoxListener(this)
            .monitorTabs(ChatboxFilterTab.GAME, ChatboxFilterTab.ALL);

        if (forceGameTabMonitoring) {
            chatListener.setAutoSwitchToTab(ChatboxFilterTab.GAME);
        } else {
            chatListener.enableWrongTabWarnings();
        }

        // Enable debug logging if configured
        if (WineConfig.ENABLE_DEBUG_LOGGING) {
            chatListener.enableDebugLogging();
            ScriptLogger.debug(this, "Chat listener debug logging enabled");
        }

        for (String trigger : WineConfig.CHATBOX_HOP_TRIGGERS) {
            final String pattern = trigger; // Capture for lambda
            chatListener.on(pattern, msg -> {
                ScriptLogger.warning(this, "Chat hop triggered ['" + pattern + "']: " + msg.getRaw());
                chatHopTriggered = true;
            });
        }

        chatListener.on("space to hold that item", msg -> {
            ScriptLogger.warning(this, "Inventory full message detected via chat, switching to banking.");
            shouldBank = true;
        });

        registerMetric("Wines Collected", () -> wineCount, MetricType.NUMBER);
        registerMetric("Wines/Hour", () -> wineCount, MetricType.RATE, "%,d/hr");
        registerMetric("Total Value", () -> wineCount * WineConfig.WINE_VALUE, MetricType.NUMBER, "%,d gp");

        openInventoryIfNeeded();
        initialised = true;
    }

    private void openInventoryIfNeeded() {
        if (getWidgetManager().getInventory().isOpen()) {
            return;
        }

        int timeout = RandomUtils.uniformRandom(300, 500);
        pollFramesHuman(() -> {
            if (!getWidgetManager().getInventory().isOpen()) {
                return getWidgetManager().getInventory().open();
            }
            return true;
        }, timeout);
    }

    @Override
    public int poll() {
        if (!initialised) {
            initialiseIfReady();
            return WineConfig.POLL_DELAY_SHORT;
        }
        return taskManager.executeNextTask();
    }

    @Override
    public void onNewFrame() {
        // Update chatbox listener to process new messages
        // Run continuously during all phases (collection, banking, navigation)
        // The listener only processes NEW messages, so old messages won't re-trigger
        if (chatListener != null) {
            chatListener.update();
        }
    }

    @Override
    protected void onMetricsPaint(Canvas canvas) {
        try {
            // Draw wine spawn tile cube visualization
            drawWineSpawnCube(canvas);
        } catch (Exception e) {
            // Silently catch painting errors to avoid disrupting the script
        }
    }

    /**
     * Draws debug visualization for the wine spawn position.
     * Shows the tile cube in cyan, filled with green if wine is detected.
     */
    private void drawWineSpawnCube(Canvas canvas) {
        // Get tile cube for wine spawn position
        Polygon wineCube = getSceneProjector().getTileCube(
            WineConfig.WINE_SPAWN_POSITION,
            WineConfig.WINE_CUBE_HEIGHT
        );

        if (wineCube == null) {
            return;  // Wine position not on screen
        }

        // Resize to match detection logic
        Polygon resizedCube = wineCube.getResized(WineConfig.WINE_CUBE_RESIZE_FACTOR);

        // Check if wine is detected
        boolean wineDetected = detectWineForDebug(resizedCube);

        if (wineDetected) {
            // Fill with semi-transparent green if wine found
            canvas.fillPolygon(resizedCube, 0x00FF00, 0.3);  // Green with 30% opacity
            canvas.drawPolygon(resizedCube, 0x00FF00, 1.0);  // Bright green outline
        } else {
            // Fill with semi-transparent cyan if no wine
            canvas.fillPolygon(resizedCube, 0x00FFFF, 0.2);  // Cyan with 20% opacity
            canvas.drawPolygon(resizedCube, 0x00FFFF, 1.0);  // Bright cyan outline
        }

        // Draw text label
        Rectangle bounds = resizedCube.getBounds();
        if (bounds != null) {
            int centerX = bounds.x + bounds.width / 2;
            int centerY = bounds.y + bounds.height / 2;

            String statusText = wineDetected ? "WINE FOUND" : "NO WINE";
            int textColor = wineDetected ? 0x00FF00 : 0xFFFFFF;

            canvas.drawText(statusText, centerX - 30, centerY - 10, textColor,
                new java.awt.Font("Arial", java.awt.Font.BOLD, 12));
        }
    }

    /**
     * Helper method to detect wine for debug painting (simplified version).
     * @param resizedCube The resized tile cube to check
     * @return true if wine color is detected
     */
    private boolean detectWineForDebug(Polygon resizedCube) {
        try {
            SearchablePixel winePixel = new SearchablePixel(
                WineConfig.WINE_BOTTLE_COLOR,
                new SingleThresholdComparator(WineConfig.WINE_COLOR_TOLERANCE),
                ColorModel.HSL
            );

            Point foundPixel = getPixelAnalyzer().findPixel(resizedCube, winePixel);
            return foundPixel != null;
        } catch (Exception e) {
            return false;  // Return false if detection fails
        }
    }

    @Override
    protected void onMetricsStop() {
        if (chatListener != null) {
            chatListener.clearHandlers();
            chatListener = null;
        }
        settingsConfirmed = false;
        initialised = false;
        taskManager = null;
    }

    public void incrementWineCount() {
        wineCount++;
        if (autoStopEnabled && wineCount >= autoStopLimit) {
            ScriptLogger.warning(this, "Auto-stop triggered after collecting " + wineCount + " wines.");
            stop();
        }
    }

    public int getWineCount() {
        return wineCount;
    }

    public void setShouldBank(boolean shouldBank) {
        this.shouldBank = shouldBank;
    }

    public boolean shouldBank() {
        return shouldBank;
    }

    /**
     * Checks if a chat hop has been triggered by the chatbox listener.
     * @return true if chat hop message was detected
     */
    public boolean isChatHopTriggered() {
        return chatHopTriggered;
    }

    /**
     * Clears the chat hop flag after processing.
     * Should be called after initiating a world hop.
     */
    public void clearChatHopFlag() {
        chatHopTriggered = false;
    }

    /**
     * Gets the chatbox listener instance.
     * @return The chat listener
     */
    public ChatBoxListener getChatListener() {
        return chatListener;
    }

    @Override
    public int[] regionsToPrioritise() {
        return new int[] {6191};
    }
}

