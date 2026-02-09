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
    threadUrl = "https://wiki.osmb.co.uk/article/jorkhunter-box-trapper",
    skillCategory = SkillCategory.HUNTER
)
public class JorkHunterBirdSnares extends JorkHunter {
    public JorkHunterBirdSnares(Object scriptCore) {
        super(scriptCore);
    }
}