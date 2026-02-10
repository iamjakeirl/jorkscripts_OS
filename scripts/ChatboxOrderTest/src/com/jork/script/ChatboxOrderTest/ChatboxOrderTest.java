package com.jork.script.ChatboxOrderTest;

import com.osmb.api.script.Script;
import com.osmb.api.script.ScriptDefinition;
import com.osmb.api.script.SkillCategory;
import com.jork.utils.ScriptLogger;

/**
 * Diagnostic script to verify chatbox message ordering.
 * Run this and perform actions that generate chatbox messages.
 */
@ScriptDefinition(
    name = "Chatbox Order Test",
    author = "Jork",
    version = 1.0,
    description = "Tests chatbox message ordering",
    skillCategory = SkillCategory.OTHER
)
public class ChatboxOrderTest extends Script {
    private int frameCount = 0;

    public ChatboxOrderTest(Object core) {
        super(core);
    }

    @Override
    public void onStart() {
        ScriptLogger.startup(this, "1.0", "Jork", "Testing chatbox order");
        ScriptLogger.info(this, "Perform actions that generate chatbox messages");
        ScriptLogger.info(this, "Watch the console to see message ordering");
    }

    @Override
    public int poll() {
        frameCount++;

        // Only check every 50 frames to avoid spam
        if (frameCount % 50 != 0) {
            return 100;
        }

        var chatboxLines = getWidgetManager().getChatbox().getText();

        if (chatboxLines.isNotVisible() || chatboxLines.isEmpty()) {
            return 100;
        }

        ScriptLogger.info(this, "=== Chatbox Contents (frame " + frameCount + ") ===");
        var lines = chatboxLines.asList();
        for (int i = 0; i < lines.size(); i++) {
            ScriptLogger.info(this, "  [" + i + "] " + lines.get(i));
        }

        return 100;
    }

    @Override
    public void onStop() {
        ScriptLogger.shutdown(this, "Test complete");
    }
}
