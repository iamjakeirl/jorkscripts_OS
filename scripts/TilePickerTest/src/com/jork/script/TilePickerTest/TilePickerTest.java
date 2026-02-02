// Intentionally in default package so OSMB external loader recognizes it (like examples)
package com.jork.script.TilePickerTest;
import com.osmb.api.script.Script;
import com.osmb.api.script.ScriptDefinition;
import com.osmb.api.script.SkillCategory;
import com.osmb.api.location.position.types.WorldPosition;
import com.jork.utils.ScriptLogger;
import com.jork.utils.tilepicker.EnhancedTilePickerPanel;
import com.jork.utils.tilepicker.TileCategory;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Practical test script for EnhancedTilePickerPanel.
 * Shows one tile picker and logs the results.
 */
@ScriptDefinition(
    author = "Jork",
    name = "Tile Picker Test",
    description = "Test the enhanced tile picker",
    skillCategory = SkillCategory.HUNTER , version = 1.0
)
public class TilePickerTest extends Script {
    
    private Map<String, List<WorldPosition>> selectedTiles;
    private boolean pickerShown = false;
    
    public TilePickerTest(Object scriptCore) {
        super(scriptCore);
    }
    
    @Override
    public int poll() {
        // Wait until we have a valid world position before showing picker
        if (!pickerShown && getWorldPosition() != null) {
            pickerShown = true;
            
            // Test the categorized selection with custom names
            ScriptLogger.info(this, "Showing tile picker with categories...");
            
            List<TileCategory> categories = Arrays.asList(
                new TileCategory("primary", "Primary Location", Color.BLUE, 1),
                new TileCategory("secondary", "Secondary Locations", Color.GREEN, 5),
                new TileCategory("optional", "Optional Areas", Color.YELLOW)
            );
            
            selectedTiles = EnhancedTilePickerPanel.showWithCategories(this, categories);
            
            // Log the results
            ScriptLogger.info(this, "=== Tile Selection Results ===");
            for (Map.Entry<String, List<WorldPosition>> entry : selectedTiles.entrySet()) {
                String categoryId = entry.getKey();
                List<WorldPosition> positions = entry.getValue();
                
                ScriptLogger.info(this, "Category '" + categoryId + "': " + positions.size() + " tiles selected");
                for (WorldPosition pos : positions) {
                    ScriptLogger.debug(this, "  - Position: [" + pos.getX() + ", " + pos.getY() + ", " + pos.getPlane() + "]");
                }
            }
            
            // Test backwards compatibility - uncomment to test single selection
            // WorldPosition single = EnhancedTilePickerPanel.show(this);
            // ScriptLogger.info(this, "Single tile selected: " + single);
        }
        
        // Keep script running to see the logs
        return random(1000, 2000);
    }

    @Override
    public void onStart() {
        ScriptLogger.startup(this, "1.0", "Jork", "Tile Picker Test");
        ScriptLogger.info(this, "Script started - tile picker will show after game loads");
    }
}
