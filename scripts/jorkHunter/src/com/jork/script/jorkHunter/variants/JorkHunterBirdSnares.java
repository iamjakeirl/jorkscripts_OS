package com.jork.script.jorkHunter.variants;

import com.jork.script.jorkHunter.JorkHunter;
import com.osmb.api.script.ScriptDefinition;
import com.osmb.api.script.SkillCategory;

/**
 * Bird Snares only variant of JorkHunter
 */
@ScriptDefinition(
    name = "jorkHunter - Bird Snares",
    author = "jork",
    version = 1.0,
    description = "Automated bird snaring for crimson swifts, copper longtails, and tropical wagtails.",
    skillCategory = SkillCategory.HUNTER
)
public class JorkHunterBirdSnares extends JorkHunter {
    public JorkHunterBirdSnares(Object scriptCore) {
        super(scriptCore);
    }
}