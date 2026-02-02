package com.jork.utils.chat;

import com.jork.utils.ScriptLogger;
import com.osmb.api.script.Script;
import com.osmb.api.shape.Rectangle;
import com.osmb.api.ui.chatbox.ChatboxFilterTab;
import com.osmb.api.utils.CachedObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Instance-based chatbox listener that monitors game messages and triggers handlers.
 *
 * <p><b>CRITICAL MESSAGE ORDERING ASSUMPTION:</b>
 * This listener assumes chatbox messages are ordered with newest messages at index 0
 * and older messages at higher indices. The suffix-matching algorithm relies on this
 * ordering to detect new messages. If OSMB's getText() ordering changes, this
 * listener will malfunction.
 *
 * <p><b>SILENT FAILURE MODE - CHAT TAB REQUIREMENTS:</b>
 * By default, this listener monitors the GAME and ALL filter tabs. If the player manually
 * switches to another tab (PUBLIC, PRIVATE, CLAN, etc.), the listener will silently
 * stop processing messages until a monitored tab is active again.
 *
 * <p>To change this behavior:
 * <ul>
 *   <li>{@link #monitorTab(ChatboxFilterTab)} - Monitor a specific tab</li>
 *   <li>{@link #monitorTabs(ChatboxFilterTab...)} - Monitor multiple tabs (reads from whichever is active)</li>
 *   <li>{@link #setAutoSwitchToTab(ChatboxFilterTab)} - Auto-switch to a specific tab on every update</li>
 *   <li>{@link #enableWrongTabWarnings()} - Log warnings when messages are skipped due to wrong tab</li>
 * </ul>
 *
 * <p>Features:
 * <ul>
 *   <li>Fluent API for registering message handlers</li>
 *   <li>Multiple handlers per pattern (no silent overwrites)</li>
 *   <li>Optional handler naming for tracking and debugging</li>
 *   <li>Auto-removal after N triggers (one-time or counted handlers)</li>
 *   <li>Individual handler removal by reference or name</li>
 *   <li>Automatic delay management (minimenu overlap, tap detection)</li>
 *   <li>Configurable chat tab monitoring</li>
 *   <li>Integration with ScriptLogger for debugging</li>
 * </ul>
 *
 * <p><b>Note:</b> This class is not thread-safe. Configuration methods should be
 * called during script initialization (typically in {@code onStart()}).
 *
 * <p><b>Basic Handler Registration Examples:</b>
 * <pre>{@code
 * // Simple unnamed handler (unlimited triggers)
 * listener.on("you catch", msg -> fishCaught++);
 *
 * // Named handler for tracking
 * listener.on("you catch", msg -> fishCaught++)
 *     .named("fish-counter");
 *
 * // Multiple handlers for same pattern (both execute)
 * listener.on("you catch", msg -> updateFishCount()).named("counter");
 * listener.on("you catch", msg -> playSound()).named("sound-fx");
 *
 * // One-time handler (auto-removes after 1 trigger)
 * listener.onOnce("you die", msg -> {
 *     ScriptLogger.warning(script, "Player died!");
 *     stop();
 * }).named("death-handler");
 *
 * // Counted handler (auto-removes after 100 triggers)
 * listener.onTimes("you catch", 100, msg -> birdsCaught++)
 *     .named("bird-limit");
 * }</pre>
 *
 * <p><b>Handler Removal Examples:</b>
 * <pre>{@code
 * // Remove by name
 * listener.remove("fish-counter");
 *
 * // Remove by reference
 * HandlerRegistration reg = listener.on("test", msg -> {});
 * reg.remove();
 * // or
 * listener.remove(reg);
 *
 * // Conditional removal
 * if (fishCaught >= 1000) {
 *     listener.remove("fish-counter");
 * }
 *
 * // Check before removing
 * if (listener.hasHandler("fish-counter")) {
 *     listener.remove("fish-counter");
 * }
 *
 * // Clear all handlers
 * listener.clearHandlers();
 * }</pre>
 *
 * <p><b>Handler Introspection Examples:</b>
 * <pre>{@code
 * // Get handler information
 * HandlerRegistration reg = listener.getHandler("fish-counter");
 * if (reg != null) {
 *     ScriptLogger.info(script, "Triggered " + reg.getTriggerCount() + " times");
 *     ScriptLogger.info(script, "Pattern: " + reg.getPattern());
 *     if (reg.getMaxTriggers() != null) {
 *         int remaining = reg.getMaxTriggers() - reg.getTriggerCount();
 *         ScriptLogger.info(script, "Remaining: " + remaining);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Tab Monitoring Examples:</b>
 * <pre>{@code
 * // Default - GAME + ALL tabs
 * chatListener = new ChatBoxListener(this)
 *     .on("you catch", msg -> fishCaught++)
 *     .enableDebugLogging();
 *
 * // Allow player to chat while botting
 * chatListener = new ChatBoxListener(this)
 *     .monitorTabs(ChatboxFilterTab.GAME, ChatboxFilterTab.PUBLIC)
 *     .on("you catch", msg -> fishCaught++);
 * // Player can switch between GAME and PUBLIC tabs freely
 *
 * // Force GAME tab (auto-switch)
 * chatListener = new ChatBoxListener(this)
 *     .setAutoSwitchToTab(ChatboxFilterTab.GAME)
 *     .on("you catch", msg -> fishCaught++);
 * // Automatically switches to GAME tab on every update
 * }</pre>
 *
 * <p><b>Complete Integration Example:</b>
 * <pre>{@code
 * private ChatBoxListener chatListener;
 * private int fishCaught = 0;
 *
 * @Override
 * public void onStart() {
 *     chatListener = new ChatBoxListener(this)
 *         .on("you catch", msg -> fishCaught++).named("fish-counter")
 *         .onOnce("you die", msg -> stop()).named("death-handler")
 *         .onTimes("you fail", 5, msg -> {
 *             ScriptLogger.warning(this, "Too many failures!");
 *             stop();
 *         }).named("failure-limit")
 *         .enableDebugLogging();
 * }
 *
 * @Override
 * public void onNewFrame() {
 *     chatListener.update();
 *
 *     // Conditional handler removal
 *     if (fishCaught >= 1000) {
 *         chatListener.remove("fish-counter");
 *         ScriptLogger.info(this, "Reached 1000 fish!");
 *     }
 * }
 * }</pre>
 */
public class ChatBoxListener {
    private final Script script;
    private final ChatBoxDelay readDelay;
    private final List<HandlerRegistration> registrations;
    private final List<HandlerRegistration> pendingRemovals;
    private final Map<String, HandlerRegistration> namedHandlers;
    private final List<String> previousChatboxLines;
    private final Set<ChatboxFilterTab> monitoredTabs;

    private boolean debugLogging;
    private long tapDelayMillis;
    private long lastChatBoxRead;
    private long lastChatBoxChange;
    private ChatboxFilterTab autoSwitchTab;
    private boolean warnOnWrongTab;
    private ChatboxFilterTab previousMonitoredTab;

    /**
     * Creates a new ChatBoxListener for the given script.
     * Default configuration: monitors GAME + ALL tabs, no auto-switching.
     *
     * @param script The script instance (used to access game API)
     */
    public ChatBoxListener(Script script) {
        this.script = script;
        this.readDelay = new ChatBoxDelay(1500);
        this.registrations = new ArrayList<>();
        this.pendingRemovals = new ArrayList<>();
        this.namedHandlers = new LinkedHashMap<>();
        this.previousChatboxLines = new ArrayList<>();
        this.monitoredTabs = new HashSet<>();
        this.monitoredTabs.add(ChatboxFilterTab.GAME);
        this.monitoredTabs.add(ChatboxFilterTab.ALL);
        this.debugLogging = false;
        this.tapDelayMillis = 1500;
        this.lastChatBoxRead = 0;
        this.lastChatBoxChange = 0;
        this.autoSwitchTab = null; // null = no auto-switching
        this.warnOnWrongTab = false;
        this.previousMonitoredTab = null;
    }

    /**
     * Registers a message handler for messages containing the given pattern (case-insensitive).
     * Handlers are executed in the order they are registered.
     *
     * <p>Multiple handlers can be registered for the same pattern - all matching handlers will execute.
     *
     * <p>Example:
     * <pre>{@code
     * // Unnamed handler
     * listener.on("you catch", msg -> fishCaught++);
     *
     * // Named handler (enables removal by name)
     * listener.on("you catch", msg -> updateUI())
     *     .named("ui-updater");
     * }</pre>
     *
     * @param pattern The substring pattern to match
     * @param handler The handler to execute when a matching message is found
     * @return The registration object (call {@link HandlerRegistration#named(String)} to name it)
     */
    public HandlerRegistration on(String pattern, ChatBoxMessageHandler handler) {
        if (pattern == null || handler == null) {
            throw new IllegalArgumentException("Pattern and handler must not be null");
        }

        HandlerRegistration registration = new HandlerRegistration(
            this, pattern.toLowerCase(), handler, null
        );
        registrations.add(registration);

        if (debugLogging) {
            ScriptLogger.debug(script, "Registered handler for pattern: '" + pattern + "' (unlimited)");
        }

        return registration;
    }

    /**
     * Registers a one-time message handler that auto-removes after first trigger.
     *
     * <p>Example:
     * <pre>{@code
     * listener.onOnce("you die", msg -> {
     *     ScriptLogger.warning(script, "Player died!");
     *     stop();
     * }).named("death-handler");
     * }</pre>
     *
     * @param pattern The substring pattern to match
     * @param handler The handler to execute when a matching message is found
     * @return The registration object (call {@link HandlerRegistration#named(String)} to name it)
     */
    public HandlerRegistration onOnce(String pattern, ChatBoxMessageHandler handler) {
        if (pattern == null || handler == null) {
            throw new IllegalArgumentException("Pattern and handler must not be null");
        }

        HandlerRegistration registration = new HandlerRegistration(
            this, pattern.toLowerCase(), handler, 1
        );
        registrations.add(registration);

        if (debugLogging) {
            ScriptLogger.debug(script, "Registered one-time handler for pattern: '" + pattern + "'");
        }

        return registration;
    }

    /**
     * Registers a counted message handler that auto-removes after N triggers.
     *
     * <p>Example:
     * <pre>{@code
     * listener.onTimes("you catch", 100, msg -> birdsCaught++)
     *     .named("bird-limit");
     * }</pre>
     *
     * @param pattern     The substring pattern to match
     * @param maxTriggers Maximum number of times to trigger (must be > 0)
     * @param handler     The handler to execute when a matching message is found
     * @return The registration object (call {@link HandlerRegistration#named(String)} to name it)
     */
    public HandlerRegistration onTimes(String pattern, int maxTriggers, ChatBoxMessageHandler handler) {
        if (pattern == null || handler == null) {
            throw new IllegalArgumentException("Pattern and handler must not be null");
        }
        if (maxTriggers <= 0) {
            throw new IllegalArgumentException("maxTriggers must be > 0");
        }

        HandlerRegistration registration = new HandlerRegistration(
            this, pattern.toLowerCase(), handler, maxTriggers
        );
        registrations.add(registration);

        if (debugLogging) {
            ScriptLogger.debug(script, "Registered counted handler for pattern: '" +
                pattern + "' (max: " + maxTriggers + ")");
        }

        return registration;
    }

    /**
     * Package-private helper called by {@link HandlerRegistration#named(String)}.
     * Registers a handler in the named handlers map for fast lookup.
     *
     * <p><b>Auto-Replace Behavior:</b> If a handler with the same name already exists
     * and is a different registration, the old handler is automatically removed.
     *
     * <p><b>Rename Support:</b> If this is a rename (previousName is set), the old
     * map entry is cleaned up.
     *
     * @param registration The registration to name
     */
    void registerNamed(HandlerRegistration registration) {
        String desiredName = registration.getName();
        if (desiredName == null || desiredName.isBlank()) {
            throw new IllegalArgumentException("Handler name must not be null or blank");
        }

        // Clean up old mapping if this is a rename
        String mappedName = registration.getMappedName();
        if (mappedName != null && !mappedName.equals(desiredName)) {
            removeNameMapping(registration);
        }

        HandlerRegistration existing = namedHandlers.get(desiredName);
        if (existing != null && existing != registration) {
            if (debugLogging) {
                ScriptLogger.debug(script, "Replacing existing handler '" + desiredName +
                    "' with new handler");
            }
            remove(existing);
        }

        namedHandlers.put(desiredName, registration);
        registration.setMappedName(desiredName);

        if (debugLogging && (existing == null || existing != registration)) {
            ScriptLogger.debug(script, "Named handler '" + desiredName + "' for pattern: '" +
                registration.getPattern() + "'");
        }
    }

    /**
     * Removes a specific handler registration.
     * After removal, the handler will no longer execute for new messages.
     *
     * <p><b>CRITICAL:</b> The handler is marked as removed immediately, preventing
     * it from executing even once more before the cleanup phase.
     *
     * <p>Example:
     * <pre>{@code
     * HandlerRegistration reg = listener.on("test", msg -> {});
     * listener.remove(reg);
     * assert !reg.isActive();  // Immediately inactive
     * }</pre>
     *
     * @param registration The registration to remove
     */
    public void remove(HandlerRegistration registration) {
        if (registration == null) {
            return;
        }

        registration.markRemoved();

        // Remove name mapping immediately so new handlers can reuse the name
        removeNameMapping(registration);

        if (!pendingRemovals.contains(registration)) {
            pendingRemovals.add(registration);
        }

        if (debugLogging) {
            String name = registration.getName();
            if (name != null) {
                ScriptLogger.debug(script, "Removing handler '" + name + "'");
            } else {
                ScriptLogger.debug(script, "Removing unnamed handler for pattern: '" +
                    registration.getPattern() + "'");
            }
        }
    }

    private void removeNameMapping(HandlerRegistration registration) {
        String mappedName = registration.getMappedName();
        if (mappedName != null) {
            HandlerRegistration current = namedHandlers.get(mappedName);
            if (current == registration) {
                namedHandlers.remove(mappedName);
            }
            registration.clearMappedName();
        }
    }

    /**
     * Removes a handler by name.
     *
     * <p>Example:
     * <pre>{@code
     * listener.on("you catch", msg -> fishCaught++)
     *     .named("fish-counter");
     * // Later...
     * listener.remove("fish-counter");
     * }</pre>
     *
     * @param name The name of the handler to remove
     * @return true if a handler was found and removed, false otherwise
     */
    public boolean remove(String name) {
        if (name == null) {
            return false;
        }

        HandlerRegistration registration = namedHandlers.get(name);
        if (registration != null) {
            remove(registration);
            return true;
        }

        return false;
    }

    /**
     * Checks if a named handler exists.
     *
     * <p>Example:
     * <pre>{@code
     * if (listener.hasHandler("fish-counter")) {
     *     ScriptLogger.info(script, "Fish counter is active");
     * }
     * }</pre>
     *
     * @param name The handler name to check
     * @return true if a handler with this name exists and is active
     */
    public boolean hasHandler(String name) {
        if (name == null) {
            return false;
        }

        HandlerRegistration registration = namedHandlers.get(name);
        return registration != null && registration.isActive();
    }

    /**
     * Gets a named handler for introspection.
     *
     * <p>Example:
     * <pre>{@code
     * HandlerRegistration reg = listener.getHandler("fish-counter");
     * if (reg != null) {
     *     int count = reg.getTriggerCount();
     *     ScriptLogger.info(script, "Triggered " + count + " times");
     * }
     * }</pre>
     *
     * @param name The handler name to look up
     * @return The registration, or null if not found
     */
    public HandlerRegistration getHandler(String name) {
        if (name == null) {
            return null;
        }

        return namedHandlers.get(name);
    }

    /**
     * Enables debug logging for chatbox events.
     * When enabled, all new messages and handler executions will be logged.
     *
     * @return This listener instance for method chaining
     */
    public ChatBoxListener enableDebugLogging() {
        this.debugLogging = true;
        ScriptLogger.debug(script, "ChatBox debug logging enabled");
        return this;
    }

    /**
     * Disables debug logging.
     *
     * @return This listener instance for method chaining
     */
    public ChatBoxListener disableDebugLogging() {
        this.debugLogging = false;
        return this;
    }

    /**
     * Sets the delay duration after a tap over the chatbox.
     * Default is 1500ms.
     *
     * @param millis The delay in milliseconds
     * @return This listener instance for method chaining
     */
    public ChatBoxListener setTapDelay(long millis) {
        this.tapDelayMillis = millis;
        return this;
    }

    /**
     * Sets which chat tab to monitor for messages.
     * Default: ChatboxFilterTab.GAME and ChatboxFilterTab.ALL
     *
     * @param tab The tab to monitor
     * @return This listener instance for method chaining
     */
    public ChatBoxListener monitorTab(ChatboxFilterTab tab) {
        if (tab == null) {
            throw new IllegalArgumentException("Tab cannot be null");
        }
        this.monitoredTabs.clear();
        this.monitoredTabs.add(tab);
        if (debugLogging) {
            ScriptLogger.debug(script, "Now monitoring tab: " + tab);
        }
        return this;
    }

    /**
     * Sets multiple chat tabs to monitor simultaneously.
     * Messages from ANY of these tabs will trigger handlers (whichever tab is currently active).
     *
     * <p>Note: This does NOT read from multiple tabs at once. It allows the listener
     * to continue working when the player switches between the specified tabs.
     *
     * <p>Example: Monitor GAME messages while allowing player to view PUBLIC chat
     * <pre>{@code
     * listener.monitorTabs(ChatboxFilterTab.GAME, ChatboxFilterTab.PUBLIC);
     * // Player can switch between GAME and PUBLIC tabs
     * // Handlers will trigger on messages from whichever tab is active
     * }</pre>
     *
     * @param tabs The tabs to monitor (varargs)
     * @return This listener instance for method chaining
     */
    public ChatBoxListener monitorTabs(ChatboxFilterTab... tabs) {
        if (tabs == null || tabs.length == 0) {
            throw new IllegalArgumentException("Must specify at least one tab");
        }
        this.monitoredTabs.clear();
        Collections.addAll(this.monitoredTabs, tabs);
        if (debugLogging) {
            ScriptLogger.debug(script, "Now monitoring tabs: " + Arrays.toString(tabs));
        }
        return this;
    }

    /**
     * Automatically switches to the specified tab when update() is called.
     * Use this to force a specific tab to be active.
     *
     * <p><b>Note:</b> This will override the player's manual tab selection on every frame.
     *
     * @param tab The tab to auto-switch to (null to disable auto-switching)
     * @return This listener instance for method chaining
     */
    public ChatBoxListener setAutoSwitchToTab(ChatboxFilterTab tab) {
        this.autoSwitchTab = tab;
        if (debugLogging) {
            ScriptLogger.debug(script, "Auto-switch tab set to: " + tab);
        }
        return this;
    }

    /**
     * Enables warning logs when messages are skipped due to wrong tab being active.
     * Useful for debugging why handlers aren't firing.
     *
     * @return This listener instance for method chaining
     */
    public ChatBoxListener enableWrongTabWarnings() {
        this.warnOnWrongTab = true;
        if (debugLogging) {
            ScriptLogger.debug(script, "Wrong tab warnings enabled");
        }
        return this;
    }

    /**
     * Disables warning logs for wrong tab.
     *
     * @return This listener instance for method chaining
     */
    public ChatBoxListener disableWrongTabWarnings() {
        this.warnOnWrongTab = false;
        return this;
    }

    /**
     * Gets the currently active chat tab.
     *
     * @return The active ChatboxFilterTab, or null if chatbox is not available
     */
    public ChatboxFilterTab getCurrentTab() {
        try {
            return script.getWidgetManager().getChatbox().getActiveFilterTab();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Checks if the listener is currently monitoring messages.
     * Returns false if the active tab is not in the monitored tabs set.
     *
     * @return true if actively monitoring, false if silent due to wrong tab
     */
    public boolean isActivelyMonitoring() {
        ChatboxFilterTab activeTab = getCurrentTab();
        return activeTab != null && monitoredTabs.contains(activeTab);
    }

    /**
     * Main update method - should be called from the script's onNewFrame() method.
     * Checks for new chatbox messages and triggers registered handlers.
     */
    public void update() {
        updateChatBoxLines();
    }

    /**
     * Gets the time in milliseconds since the last chatbox read.
     *
     * @return Milliseconds since last read, or 0 if never read
     */
    public long getTimeSinceLastRead() {
        if (lastChatBoxRead == 0) {
            return 0;
        }
        return System.currentTimeMillis() - lastChatBoxRead;
    }

    /**
     * Gets the time in milliseconds since the last chatbox change (new message).
     *
     * @return Milliseconds since last change, or 0 if no changes detected
     */
    public long getTimeSinceLastChange() {
        if (lastChatBoxChange == 0) {
            return 0;
        }
        return System.currentTimeMillis() - lastChatBoxChange;
    }

    /**
     * Clears all registered handlers.
     * Removes both named and unnamed handlers.
     */
    public void clearHandlers() {
        registrations.clear();
        namedHandlers.clear();
        pendingRemovals.clear();
        if (debugLogging) {
            ScriptLogger.debug(script, "Cleared all chatbox handlers");
        }
    }

    /**
     * Clears the chatbox line history.
     * Useful when you want to reset state or avoid re-processing old messages.
     */
    public void clearHistory() {
        previousChatboxLines.clear();
        if (debugLogging) {
            ScriptLogger.debug(script, "Cleared chatbox history");
        }
    }

    /**
     * Gets the current number of active registered handlers.
     * This includes both named and unnamed handlers.
     *
     * @return Number of active handlers (excludes pending removals)
     */
    public int getHandlerCount() {
        return registrations.size() - pendingRemovals.size();
    }

    /**
     * Checks if the chatbox reader is currently delayed.
     *
     * @return true if delayed (due to minimenu or tap)
     */
    public boolean isDelayed() {
        return readDelay.isActive();
    }

    private void updateChatBoxLines() {
        // Auto-switch to configured tab if enabled
        if (autoSwitchTab != null) {
            ChatboxFilterTab currentTab = script.getWidgetManager().getChatbox().getActiveFilterTab();
            if (currentTab != autoSwitchTab) {
                if (debugLogging) {
                    ScriptLogger.debug(script, "Auto-switching to tab: " + autoSwitchTab);
                }
                script.getWidgetManager().getChatbox().openFilterTab(autoSwitchTab);
                return; // Wait for next frame after switch
            }
        }

        // Check if current tab is in monitored tabs
        ChatboxFilterTab activeTab = script.getWidgetManager().getChatbox().getActiveFilterTab();
        boolean isOnMonitoredTab = monitoredTabs.contains(activeTab);

        // Clear history when switching to a monitored tab (prevents message burst)
        if (isOnMonitoredTab && activeTab != previousMonitoredTab) {
            if (debugLogging) {
                ScriptLogger.debug(script, "Switched to monitored tab '" + activeTab +
                    "' - clearing history to prevent message burst");
            }
            clearHistory();
        }

        // Track the current monitored tab for next frame
        previousMonitoredTab = isOnMonitoredTab ? activeTab : null;

        if (!isOnMonitoredTab) {
            if (warnOnWrongTab) {
                ScriptLogger.warning(script,
                    "Chatbox listener inactive - active tab: " + activeTab +
                    ", monitored tabs: " + monitoredTabs);
            }
            return;
        }

        // Get chatbox bounds
        Rectangle chatboxBounds = script.getWidgetManager().getChatbox().getBounds();
        if (chatboxBounds == null) {
            return;
        }

        // Check if minimenu overlaps chatbox
        CachedObject<Rectangle> minimenuBounds = script.getWidgetManager().getMiniMenu().getMenuBounds();
        if (minimenuBounds != null && minimenuBounds.getScreenUUID() != null
                && minimenuBounds.getScreenUUID().equals(script.getScreen().getUUID())) {
            if (minimenuBounds.getObject().intersects(chatboxBounds)) {
                if (debugLogging) {
                    ScriptLogger.debug(script, "Minimenu intersects chatbox - activating delay");
                }
                readDelay.activate();
                return;
            }
        }

        // Check if we recently tapped over the chatbox (causes text reading issues)
        Rectangle chatboxBounds2 = chatboxBounds.getPadding(0, 0, 12, 0);
        long lastTapMillis = script.getFinger().getLastTapMillis();
        if (chatboxBounds2.contains(script.getFinger().getLastTapX(), script.getFinger().getLastTapY())
                && System.currentTimeMillis() - lastTapMillis < tapDelayMillis) {
            if (debugLogging) {
                ScriptLogger.debug(script, "Recent tap over chatbox - activating delay");
            }
            readDelay.activate();
            return;
        }

        // Return if delay is active
        if (readDelay.isActive()) {
            return;
        }

        // Read current chatbox lines
        var currentChatboxLines = script.getWidgetManager().getChatbox().getText();
        if (currentChatboxLines.isNotVisible()) {
            if (debugLogging) {
                ScriptLogger.debug(script, "Chatbox not visible");
            }
            return;
        }

        List<String> currentLines = currentChatboxLines.asList();
        if (currentLines.isEmpty()) {
            return;
        }

        // Get new lines
        List<String> newLines = getNewLines(currentLines, previousChatboxLines);

        // Update previous lines
        previousChatboxLines.clear();
        previousChatboxLines.addAll(currentLines);

        // Process new messages
        if (!newLines.isEmpty()) {
            onNewChatBoxMessages(newLines);
        }
    }

    private void onNewChatBoxMessages(List<String> newLines) {
        for (String line : newLines) {
            ChatBoxMessage message = new ChatBoxMessage(line);

            if (debugLogging) {
                ScriptLogger.debug(script, "New chatbox message: " + line);
            }

            // Trigger matching handlers
            for (HandlerRegistration registration : registrations) {
                // Skip inactive or exhausted handlers
                if (!registration.isActive() || !registration.shouldTrigger()) {
                    continue;
                }

                String pattern = registration.getPattern();
                if (message.contains(pattern)) {
                    if (debugLogging) {
                        String name = registration.getName();
                        if (name != null) {
                            ScriptLogger.debug(script, "Triggering handler '" + name +
                                "' for pattern: '" + pattern + "'");
                        } else {
                            ScriptLogger.debug(script, "Triggering unnamed handler for pattern: '" +
                                pattern + "'");
                        }
                    }

                    try {
                        registration.getHandler().onMessage(message);
                        registration.recordTrigger();

                        // Log auto-removal if handler was exhausted
                        if (debugLogging && registration.isExhausted()) {
                            String name = registration.getName();
                            if (name != null) {
                                ScriptLogger.debug(script, "Handler '" + name +
                                    "' auto-removed after " + registration.getTriggerCount() + " trigger(s)");
                            } else {
                                ScriptLogger.debug(script, "Unnamed handler auto-removed after " +
                                    registration.getTriggerCount() + " trigger(s)");
                            }
                        }
                    } catch (Exception e) {
                        String name = registration.getName();
                        String handlerDesc = name != null ? "'" + name + "'" : "unnamed handler";
                        ScriptLogger.exception(script, "Error in chatbox handler " + handlerDesc +
                            " for pattern: '" + pattern + "'", e);
                    }
                }
            }
        }

        // Clean up removed handlers (deferred to avoid concurrent modification)
        if (!pendingRemovals.isEmpty()) {
            for (HandlerRegistration registration : pendingRemovals) {
                registrations.remove(registration);
            }
            pendingRemovals.clear();
        }
    }

    /**
     * Detects new messages by comparing current chatbox state with previous state.
     *
     * <p>Messages appear with index 0 as the newest message, pushing older messages
     * to higher indices. This method uses suffix-matching to identify new messages
     * by finding where the current list's tail matches the previous list's head.
     *
     * <p>Example:
     * <pre>
     * previousLines: ["Mining copper", "You hit a 5"]
     * currentLines:  ["You catch shrimp", "Mining copper", "You hit a 5"]
     *
     * Matches currentLines[1..2] == previousLines[0..1]
     * Returns: ["You catch shrimp"]
     * </pre>
     *
     * @param currentLines  The current chatbox messages (index 0 = newest)
     * @param previousLines The previous chatbox messages from last frame
     * @return List of new messages that weren't in previousLines
     */
    private List<String> getNewLines(List<String> currentLines, List<String> previousLines) {
        lastChatBoxRead = System.currentTimeMillis();

        if (currentLines.isEmpty()) {
            return Collections.emptyList();
        }

        // Index where new messages end and old messages begin
        int firstDifference = 0;

        if (!previousLines.isEmpty()) {
            // Quick check: if lists are identical, no new messages
            if (currentLines.equals(previousLines)) {
                return Collections.emptyList();
            }

            int currSize = currentLines.size();
            int prevSize = previousLines.size();

            // SUFFIX-MATCHING ALGORITHM
            // -------------------------
            // Find where currentLines[i..end] matches previousLines[0..suffixLen]
            // This tells us where the old messages start in the current list
            for (int i = 0; i < currSize; i++) {
                int suffixLen = currSize - i;

                // Skip if suffix would be longer than previous list
                if (suffixLen > prevSize) {
                    continue;
                }

                // Check if currentLines[i..end] matches previousLines[0..suffixLen]
                boolean match = true;
                for (int j = 0; j < suffixLen; j++) {
                    if (!currentLines.get(i + j).equals(previousLines.get(j))) {
                        match = false;
                        break;
                    }
                }

                // If we found a match, we know messages [0..i) are new
                if (match) {
                    firstDifference = i;
                    break;
                }
            }
        }

        // Extract new messages (everything before firstDifference)
        List<String> newLines = firstDifference == 0
                ? List.copyOf(currentLines)  // All lines are new (first run or all scrolled off)
                : currentLines.subList(0, firstDifference);

        if (!newLines.isEmpty()) {
            lastChatBoxChange = System.currentTimeMillis();
        }

        return newLines;
    }
}
