package com.jork.utils.chat;

import java.util.regex.Pattern;

/**
 * Immutable wrapper for a chatbox message with helper methods for pattern matching.
 *
 * <p>Example usage:
 * <pre>{@code
 * ChatBoxMessage msg = new ChatBoxMessage("You catch a shrimp.");
 * if (msg.contains("catch")) {
 *     // Handle fishing success
 * }
 * }</pre>
 */
public final class ChatBoxMessage {
    private final String rawContent;
    private final String lowerContent;
    private final long timestamp;

    /**
     * Creates a new ChatBoxMessage with the current timestamp.
     *
     * @param content The raw message content from the chatbox
     */
    public ChatBoxMessage(String content) {
        this.rawContent = content != null ? content : "";
        this.lowerContent = this.rawContent.toLowerCase();
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Gets the raw message content (preserving original case).
     *
     * @return The original message text
     */
    public String getRaw() {
        return rawContent;
    }

    /**
     * Gets the lowercase version of the message content.
     * Useful for case-insensitive matching.
     *
     * @return The message text in lowercase
     */
    public String getLowercase() {
        return lowerContent;
    }

    /**
     * Gets the timestamp when this message was captured.
     *
     * @return Timestamp in milliseconds since epoch
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Checks if the message contains the given substring (case-insensitive).
     *
     * @param substring The substring to search for
     * @return true if the message contains the substring
     */
    public boolean contains(String substring) {
        if (substring == null) {
            return false;
        }
        return lowerContent.contains(substring.toLowerCase());
    }

    /**
     * Checks if the message contains any of the given substrings (case-insensitive).
     *
     * @param substrings The substrings to search for
     * @return true if the message contains at least one substring
     */
    public boolean containsAny(String... substrings) {
        if (substrings == null || substrings.length == 0) {
            return false;
        }
        for (String substring : substrings) {
            if (contains(substring)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the message contains all of the given substrings (case-insensitive).
     *
     * @param substrings The substrings to search for
     * @return true if the message contains all substrings
     */
    public boolean containsAll(String... substrings) {
        if (substrings == null || substrings.length == 0) {
            return false;
        }
        for (String substring : substrings) {
            if (!contains(substring)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if the message starts with the given prefix (case-insensitive).
     *
     * @param prefix The prefix to check
     * @return true if the message starts with the prefix
     */
    public boolean startsWith(String prefix) {
        if (prefix == null) {
            return false;
        }
        return lowerContent.startsWith(prefix.toLowerCase());
    }

    /**
     * Checks if the message ends with the given suffix (case-insensitive).
     *
     * @param suffix The suffix to check
     * @return true if the message ends with the suffix
     */
    public boolean endsWith(String suffix) {
        if (suffix == null) {
            return false;
        }
        return lowerContent.endsWith(suffix.toLowerCase());
    }

    /**
     * Checks if the message matches the given regex pattern.
     *
     * @param pattern The regex pattern to match against
     * @return true if the message matches the pattern
     */
    public boolean matches(Pattern pattern) {
        if (pattern == null) {
            return false;
        }
        return pattern.matcher(rawContent).find();
    }

    /**
     * Checks if the message matches the given regex pattern string.
     *
     * @param regex The regex pattern string
     * @return true if the message matches the pattern
     */
    public boolean matches(String regex) {
        if (regex == null) {
            return false;
        }
        return matches(Pattern.compile(regex));
    }

    /**
     * Checks if the message exactly equals the given text (case-insensitive).
     *
     * @param text The text to compare
     * @return true if the message equals the text
     */
    public boolean equalsIgnoreCase(String text) {
        if (text == null) {
            return false;
        }
        return lowerContent.equals(text.toLowerCase());
    }

    /**
     * Checks if the message is empty or only contains whitespace.
     *
     * @return true if the message is blank
     */
    public boolean isBlank() {
        return rawContent.isBlank();
    }

    @Override
    public String toString() {
        return rawContent;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ChatBoxMessage other = (ChatBoxMessage) obj;
        return rawContent.equals(other.rawContent) && timestamp == other.timestamp;
    }

    @Override
    public int hashCode() {
        int result = rawContent.hashCode();
        result = 31 * result + Long.hashCode(timestamp);
        return result;
    }
}
