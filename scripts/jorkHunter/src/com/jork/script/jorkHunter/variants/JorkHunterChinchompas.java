package com.jork.script.jorkHunter.variants;

import com.jork.script.jorkHunter.JorkHunter;
import com.osmb.api.script.ScriptDefinition;
import com.osmb.api.script.SkillCategory;

/**
 * Chinchompas only variant of JorkHunter
 */
@ScriptDefinition(
    name = "jorkHunter - Chinchompas",
    author = "jork",
    version = 1.0,
    description = "Automated chinchompa hunting with box traps.",
    skillCategory = SkillCategory.HUNTER
)
public class JorkHunterChinchompas extends JorkHunter {
    public JorkHunterChinchompas(Object scriptCore) {
        super(scriptCore);
    }
}