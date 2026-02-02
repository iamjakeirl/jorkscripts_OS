package com.jork.utils.chat;

/**
 * Represents a registered chatbox message handler with lifecycle management.
 *
 * <p>Supports optional naming, auto-removal after N triggers, and manual removal.
 * Instances are created by {@link ChatBoxListener} and returned from registration methods.
 *
 * <p><b>Usage Examples:</b>
 * <pre>{@code
 * // Named handler
 * HandlerRegistration reg = listener.on("you catch", msg -> fishCaught++)
 *     .namedRef("fish-counter");
 *
 * // One-time named handler
 * listener.onOnce("you die", msg -> stop())
 *     .named("death-handler");
 *
 * // Manual removal by reference
 * HandlerRegistration reg = listener.on("test", msg -> {});
 * reg.remove();
 *
 * // Introspection
 * if (reg.isActive()) {
 *     int count = reg.getTriggerCount();
 *     String pattern = reg.getPattern();
 * }
 * }</pre>
 *
 * <p><b>Note:</b> This class is not thread-safe. Handler registration and removal
 * should occur from the script thread only.
 */
public final class HandlerRegistration {
    private final ChatBoxListener listener;
    private final String pattern;
    private final ChatBoxMessageHandler handler;
    private final Integer maxTriggers; // null = unlimited

    private String name; // null = unnamed
    private String mappedName; // Last name stored in ChatBoxListener's map
    private int triggerCount;
    private boolean removed;

    /**
     * Package-private constructor. Only {@link ChatBoxListener} creates instances.
     *
     * @param listener    The listener that owns this registration
     * @param pattern     The pattern to match (lowercase)
     * @param handler     The handler to execute
     * @param maxTriggers Maximum triggers before auto-removal (null = unlimited)
     */
    HandlerRegistration(ChatBoxListener listener, String pattern,
                        ChatBoxMessageHandler handler, Integer maxTriggers) {
        this.listener = listener;
        this.pattern = pattern;
        this.handler = handler;
        this.maxTriggers = maxTriggers;
        this.name = null;
        this.triggerCount = 0;
        this.removed = false;
    }

    /**
     * Assigns a name to this handler for tracking and removal.
     * If a handler with the same name already exists, it will be automatically removed.
     *
     * <p>Supports renaming - calling this method multiple times will update the name
     * and clean up old map entries.
     *
     * <p>Example:
     * <pre>{@code
     * listener.on("you catch", msg -> fishCaught++)
     *     .named("fish-counter")
     *     .on("you fail", msg -> failures++);
     *
     * // Renaming example
     * HandlerRegistration reg = listener.on("test", msg -> {}).named("foo");
     * reg.named("bar");  // Renames from "foo" to "bar"
     * }</pre>
     *
     * @param name The name to assign (must not be null or blank)
     * @return The parent ChatBoxListener for method chaining
     * @throws IllegalArgumentException if name is null or blank
     * @throws IllegalStateException    if handler has already been removed
     */
    public ChatBoxListener named(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Handler name must not be null or blank");
        }
        if (removed) {
            throw new IllegalStateException("Cannot name a removed handler");
        }
        this.name = name;
        listener.registerNamed(this);
        return listener;
    }

    /**
     * Convenience method that assigns a name and returns the registration.
     * Useful when you want to keep the HandlerRegistration reference.
     *
     * <p>Example:
     * <pre>{@code
     * HandlerRegistration reg = listener.on("you catch", msg -> fishCaught++)
     *     .namedRef("fish-counter");
     * }</pre>
     *
     * @param name The handler name
     * @return This registration (with the name assigned)
     */
    public HandlerRegistration namedRef(String name) {
        named(name);
        return this;
    }

    /**
     * Removes this handler from its listener.
     * After removal, the handler will no longer execute for new messages.
     * Calling remove() multiple times is safe (no-op after first call).
     */
    public void remove() {
        if (!removed) {
            listener.remove(this);
        }
    }

    /**
     * Package-private method to mark this handler as removed.
     * Called by {@link ChatBoxListener} when removing a handler.
     * This ensures the handler is marked inactive immediately, preventing
     * it from firing even once more before cleanup.
     */
    void markRemoved() {
        this.removed = true;
    }

    /**
     * Checks if this handler is active (not removed and not exhausted).
     *
     * @return true if handler can still execute
     */
    public boolean isActive() {
        return !removed;
    }

    /**
     * Checks if this handler should trigger for a matching message.
     * Returns false if handler has reached max triggers or been removed.
     *
     * @return true if handler should execute
     */
    boolean shouldTrigger() {
        if (removed) {
            return false;
        }
        if (maxTriggers == null) {
            return true; // Unlimited
        }
        return triggerCount < maxTriggers;
    }

    /**
     * Records a trigger and auto-removes if max triggers reached.
     * Package-private - only called by {@link ChatBoxListener}.
     */
    void recordTrigger() {
        triggerCount++;
        if (maxTriggers != null && triggerCount >= maxTriggers) {
            remove();
        }
    }

    /**
     * Gets the handler callback.
     * Package-private - only used by {@link ChatBoxListener}.
     *
     * @return The handler callback
     */
    ChatBoxMessageHandler getHandler() {
        return handler;
    }

    /**
     * Gets the name of this handler.
     *
     * @return The handler name, or null if unnamed
     */
    public String getName() {
        return name;
    }

    /**
     * Package-private accessor used by ChatBoxListener for map bookkeeping.
     */
    String getMappedName() {
        return mappedName;
    }

    /**
     * Package-private mutator used by ChatBoxListener to remember the active map key.
     */
    void setMappedName(String mappedName) {
        this.mappedName = mappedName;
    }

    /**
     * Package-private helper to clear the stored map key after removal/rename.
     */
    void clearMappedName() {
        this.mappedName = null;
    }

    /**
     * Gets the pattern this handler matches.
     *
     * @return The pattern (lowercase)
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * Gets the number of times this handler has been triggered.
     *
     * @return Trigger count
     */
    public int getTriggerCount() {
        return triggerCount;
    }

    /**
     * Gets the maximum number of triggers before auto-removal.
     *
     * @return Maximum triggers, or null if unlimited
     */
    public Integer getMaxTriggers() {
        return maxTriggers;
    }

    /**
     * Checks if this handler has a name.
     *
     * @return true if named, false if unnamed
     */
    public boolean isNamed() {
        return name != null;
    }

    /**
     * Checks if this handler has reached its trigger limit (if any).
     *
     * @return true if max triggers reached, false otherwise
     */
    public boolean isExhausted() {
        return maxTriggers != null && triggerCount >= maxTriggers;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("HandlerRegistration[");
        if (name != null) {
            sb.append("name='").append(name).append("', ");
        }
        sb.append("pattern='").append(pattern).append("'");
        if (maxTriggers != null) {
            sb.append(", triggers=").append(triggerCount).append("/").append(maxTriggers);
        } else {
            sb.append(", triggers=").append(triggerCount);
        }
        if (removed) {
            sb.append(", REMOVED");
        }
        sb.append("]");
        return sb.toString();
    }
}
