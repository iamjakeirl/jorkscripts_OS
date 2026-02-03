package com.jork.script.jorkHunter.variants;

import com.jork.script.jorkHunter.JorkHunter;
import com.osmb.api.script.ScriptDefinition;
import com.osmb.api.script.SkillCategory;

/**
 * Box trap variant of JorkHunter.
 */
@ScriptDefinition(
    name = "jorkHunter - Box Trapper",
    author = "jork",
    version = 1.0,
    description = "Automated box trap hunting (chinchompas, jerboas, ferrets).",
    skillCategory = SkillCategory.HUNTER
)
public class JorkHunterBoxTrapper extends JorkHunter {
    public JorkHunterBoxTrapper(Object scriptCore) {
        super(scriptCore);
    }
}
