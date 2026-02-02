package com.jork.utils.chat;

/**
 * Functional interface for handling chatbox messages.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Lambda expression
 * listener.on("you catch", msg -> {
 *     fishCaught++;
 *     ScriptLogger.info(script, "Caught a fish!");
 * });
 *
 * // Method reference
 * listener.on("you die", this::handleDeath);
 * }</pre>
 */
@FunctionalInterface
public interface ChatBoxMessageHandler {
    /**
     * Handles a chatbox message that matches the registered pattern.
     *
     * @param message The message that triggered this handler
     */
    void onMessage(ChatBoxMessage message);
}
