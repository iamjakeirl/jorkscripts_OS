package com.jork.script.WineCollector.config;

import com.osmb.api.item.ItemID;
import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.location.area.impl.RectangleArea;

public class WineConfig {
    
    // Item Constants
    public static final int WINE_ID = ItemID.ECLIPSE_RED;
    public static final int WINE_VALUE = 700;
    public static final String WINE_NAME = "Eclipse red";
    public static final String WINE_GROUND_NAME = "Eclipse red";  // Name as it appears on ground (verify in-game)
    
    // Location Areas
    // RectangleArea constructor: (x, y, width, height, plane)
    public static final RectangleArea LADDER_AREA = new RectangleArea(
        1553, 3034,  // x, y position
        3, 2,        // width, height
        0            // plane (ground floor)
    );

    public static final RectangleArea LADDER_AREA_SECOND_FLOOR = new RectangleArea(
        1553, 3034,  // x, y position
        3, 2,        // width, height
        1            // plane (second floor)
    );

    public static final RectangleArea BANK_AREA = new RectangleArea(
        1542, 3039,  // x, y position
        2, 1,        // width, height
        0            // plane (ground floor)
    );

    public static final RectangleArea UPSTAIRS_AREA = new RectangleArea(
        1552, 3033,  // x, y position
        5, 2,        // width, height
        2            // plane (top floor)
    );

    // Wine Spawn Configuration
    public static final WorldPosition WINE_SPAWN_POSITION = new WorldPosition(
        1555, 3035, 2  // x, y, plane (2 = top floor)
    );

    // Wine Detection Configuration
    public static final int WINE_BOTTLE_COLOR = -5635841;  // Eclipse red wine RGB color
    public static final int WINE_COLOR_TOLERANCE = 5;      // Color matching tolerance
    public static final int WINE_CUBE_HEIGHT = 120;        // Tile cube height for wine bottle
    public static final double WINE_CUBE_RESIZE_FACTOR = 0.3;  // Shrink cube to focus tap area
    // Floor plane constants
    public static final int GROUND_FLOOR_PLANE = 0;
    public static final int SECOND_FLOOR_PLANE = 1;
    public static final int TOP_FLOOR_PLANE = 2;
    public static final int WINE_SPAWN_PLANE = 2;  // Top floor (third floor)
    
    // Timing Configuration
    public static final int PICKUP_TIMEOUT = 3000;      // 3 seconds to pick up wine
    public static final int HOP_DELAY_MIN = 800;        // Minimum delay before hopping
    public static final int HOP_DELAY_MAX = 1200;       // Maximum delay before hopping
    public static final int LADDER_CLIMB_TIMEOUT = 5000; // 5 seconds to climb ladder
    public static final int BANK_OPEN_TIMEOUT = 5000;    // 5 seconds to open bank
    public static final int NAVIGATION_TIMEOUT = 20000;  // 20 seconds for navigation
    public static final int INVENTORY_CHECK_DELAY = 400; // Human delay to check inventory visually
    public static final int ARRIVAL_DELAY = 500;         // Human delay after arriving at destination
    
    // Additional Configuration
    public static final int INVENTORY_SIZE = 28;
    public static final String LADDER_NAME = "Ladder";
    public static final String BANK_CHEST_NAME = "Bank chest";
    public static final String LADDER_UP_ACTION = "Climb-up";
    public static final String LADDER_DOWN_ACTION = "Climb-down";
    public static final String BANK_USE_ACTION = "Bank";

    // Delay configurations
    public static final int POLL_DELAY_SHORT = 300;
    public static final int POLL_DELAY_MEDIUM = 500;
    public static final int POLL_DELAY_LONG = 1000;
    public static final int POLL_DELAY_WORLD_HOP = 3000;

    // Chatbox hop trigger configuration
    // Multiple patterns for robust OCR error tolerance
    // Full message: "You're a Group Ironman, so you can't take items that non-group members have dropped."
    // Using multiple distinctive substrings increases reliability even with OCR errors
    public static final String[] CHATBOX_HOP_TRIGGERS = {
        "group ironman",        // Most distinctive phrase
        "can't take items",     // Secondary match
        "non-group members",    // Tertiary match
        "can't take item"       // Handles potential plural/singular OCR errors
    };

    // Debug configuration
    // Set to true to enable verbose chatbox message logging
    public static final boolean ENABLE_DEBUG_LOGGING = true;
}
