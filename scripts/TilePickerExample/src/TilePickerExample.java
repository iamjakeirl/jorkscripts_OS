import com.osmb.api.script.*;
import com.osmb.api.location.position.types.WorldPosition;
import com.jork.utils.ScriptLogger;
import com.jork.utils.tilepicker.EnhancedTilePickerPanel;
import com.jork.utils.tilepicker.TileCategory;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Example script demonstrating the usage of EnhancedTilePickerPanel.
 * Shows backwards compatibility and new enhanced features.
 */
@ScriptDefinition(
    name = "Tile Picker Example",
    author = "Jork",
    version = 1.0,
    description = "Demonstrates enhanced tile picker usage",
    skillCategory = SkillCategory.OTHER
)
public class TilePickerExample extends Script {
    
    public TilePickerExample(Object core) {
        super(core);
    }
    
    @Override
    public void onStart() {
        ScriptLogger.startup(this, "1.0", "Jork", "Tile Picker Demo");
        
        // Example 1: Backwards compatible single selection (same as original API)
        ScriptLogger.info(this, "Example 1: Single tile selection (backwards compatible)");
        WorldPosition singleTile = EnhancedTilePickerPanel.show(this);
        if (singleTile != null) {
            ScriptLogger.info(this, "Selected single tile at: " + singleTile);
        } else {
            ScriptLogger.info(this, "Single tile selection cancelled");
        }
        
        // Example 2: Multiple tile selection without categories
        ScriptLogger.info(this, "Example 2: Multiple tile selection");
        List<WorldPosition> multipleTiles = EnhancedTilePickerPanel.showMultiple(this);
        ScriptLogger.info(this, "Selected " + multipleTiles.size() + " tiles");
        for (WorldPosition pos : multipleTiles) {
            ScriptLogger.debug(this, "  - Tile at: " + pos);
        }
        
        // Example 3: Hunter script with custom categories
        ScriptLogger.info(this, "Example 3: Hunter-style categorized selection");
        List<TileCategory> hunterCategories = Arrays.asList(
            new TileCategory("anchor", "Hunting Anchor", Color.BLUE, 1),
            new TileCategory("traps", "Trap Positions", Color.GREEN, 5),
            new TileCategory("drops", "Drop Location", Color.YELLOW, 1)
        );
        
        Map<String, List<WorldPosition>> hunterSetup = 
            EnhancedTilePickerPanel.showWithCategories(this, hunterCategories);
        
        for (Map.Entry<String, List<WorldPosition>> entry : hunterSetup.entrySet()) {
            String categoryId = entry.getKey();
            List<WorldPosition> positions = entry.getValue();
            ScriptLogger.info(this, "Category '" + categoryId + "': " + positions.size() + " tiles");
        }
        
        // Example 4: Mining script with different categories using Builder
        ScriptLogger.info(this, "Example 4: Mining script with Builder pattern");
        Map<String, List<WorldPosition>> miningSetup = 
            EnhancedTilePickerPanel.builder(this)
                .withTitle("Mining Configuration")
                .withCategory("ores", "Iron Ore Rocks", Color.ORANGE, 3)
                .withCategory("bank_path", "Banking Path", Color.BLUE)
                .withCategory("safe_spot", "Safe Spot", Color.GREEN, 1)
                .show();
        
        ScriptLogger.info(this, "Mining setup complete:");
        ScriptLogger.info(this, "  - Ore rocks: " + miningSetup.get("ores").size());
        ScriptLogger.info(this, "  - Bank path: " + miningSetup.get("bank_path").size());
        ScriptLogger.info(this, "  - Safe spot: " + miningSetup.get("safe_spot").size());
        
        ScriptLogger.info(this, "All examples completed!");
    }
    
    @Override
    public int poll() {
        // This is just a demo script, stop after showing examples
        ScriptLogger.shutdown(this, "Tile picker examples completed");
        stop(); // Stop the script
        return 1000;
    }
}