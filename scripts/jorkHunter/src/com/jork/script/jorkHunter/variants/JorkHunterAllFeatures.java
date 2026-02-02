package com.jork.script.jorkHunter.variants;

import com.jork.script.jorkHunter.JorkHunter;
import com.osmb.api.script.ScriptDefinition;
import com.osmb.api.script.SkillCategory;

/**
 * All Features variant of JorkHunter (Development version)
 */
@ScriptDefinition(
    name = "jorkHunter (All Features)",
    author = "jork",
    version = 1.0,
    description = "A versatile All-In-One (AIO) hunter script - Development version with all trap types.",
    skillCategory = SkillCategory.HUNTER
)
public class JorkHunterAllFeatures extends JorkHunter {
    public JorkHunterAllFeatures(Object scriptCore) {
        super(scriptCore);
    }
}