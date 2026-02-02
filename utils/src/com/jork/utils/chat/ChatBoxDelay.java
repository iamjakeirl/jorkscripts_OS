package com.jork.utils.chat;

/**
 * Manages delay state for chatbox reading to prevent false positives.
 *
 * <p>Delays are activated when:
 * <ul>
 *   <li>The minimenu overlaps the chatbox area</li>
 *   <li>A tap/click occurs over the chatbox region</li>
 *   <li>Any other condition that might cause temporary text reading issues</li>
 * </ul>
 *
 * <p><b>Note:</b> This class is not thread-safe. Configuration should occur during
 * script initialization.
 *
 * <p>Example usage:
 * <pre>{@code
 * ChatBoxDelay delay = new ChatBoxDelay(1500); // 1.5 second delay
 *
 * if (tappedOverChatbox) {
 *     delay.activate();
 * }
 *
 * if (!delay.isActive()) {
 *     // Safe to read chatbox
 * }
 * }</pre>
 */
public class ChatBoxDelay {
    private final long durationMillis;
    private long activatedAt;
    private boolean active;

    /**
     * Creates a new ChatBoxDelay with the specified duration.
     *
     * @param durationMillis The delay duration in milliseconds
     */
    public ChatBoxDelay(long durationMillis) {
        this.durationMillis = durationMillis;
        this.activatedAt = 0;
        this.active = false;
    }

    /**
     * Creates a new ChatBoxDelay with the default duration (1500ms).
     */
    public ChatBoxDelay() {
        this(1500);
    }

    /**
     * Activates the delay. The delay will remain active until the duration expires.
     */
    public void activate() {
        this.activatedAt = System.currentTimeMillis();
        this.active = true;
    }

    /**
     * Manually deactivates the delay, regardless of elapsed time.
     */
    public void deactivate() {
        this.active = false;
        this.activatedAt = 0;
    }

    /**
     * Checks if the delay is currently active.
     * Automatically deactivates if the duration has expired.
     *
     * @return true if the delay is still active, false otherwise
     */
    public boolean isActive() {
        if (!active) {
            return false;
        }

        long elapsed = System.currentTimeMillis() - activatedAt;
        if (elapsed >= durationMillis) {
            deactivate();
            return false;
        }

        return true;
    }

    /**
     * Gets the remaining delay time in milliseconds.
     *
     * @return Remaining time in milliseconds, or 0 if not active
     */
    public long getRemainingMillis() {
        if (!active) {
            return 0;
        }

        long elapsed = System.currentTimeMillis() - activatedAt;
        long remaining = durationMillis - elapsed;
        return Math.max(0, remaining);
    }

    /**
     * Gets the configured delay duration.
     *
     * @return The delay duration in milliseconds
     */
    public long getDurationMillis() {
        return durationMillis;
    }

    /**
     * Checks if the delay has been activated at least once.
     *
     * @return true if activate() has been called
     */
    public boolean hasBeenActivated() {
        return activatedAt > 0;
    }

    @Override
    public String toString() {
        if (active) {
            return String.format("ChatBoxDelay[active, remaining=%dms]", getRemainingMillis());
        }
        return "ChatBoxDelay[inactive]";
    }
}
