package com.jork.utils.metrics.providers;

/**
 * OSRS XP Level data and utility methods for accurate level calculations.
 * Contains the official XP requirements for levels 1-99.
 */
public class XPLevelData {
    
    /**
     * Official OSRS XP requirements for each level.
     * Index 0 = Level 1 (0 XP), Index 98 = Level 99 (13,034,431 XP)
     */
    private static final int[] XP_TABLE = {
        0,          // Level 1
        83,         // Level 2
        174,        // Level 3
        276,        // Level 4
        388,        // Level 5
        512,        // Level 6
        650,        // Level 7
        801,        // Level 8
        969,        // Level 9
        1154,       // Level 10
        1358,       // Level 11
        1584,       // Level 12
        1833,       // Level 13
        2107,       // Level 14
        2411,       // Level 15
        2746,       // Level 16
        3115,       // Level 17
        3523,       // Level 18
        3973,       // Level 19
        4470,       // Level 20
        5018,       // Level 21
        5624,       // Level 22
        6291,       // Level 23
        7028,       // Level 24
        7842,       // Level 25
        8740,       // Level 26
        9730,       // Level 27
        10824,      // Level 28
        12031,      // Level 29
        13363,      // Level 30
        14833,      // Level 31
        16456,      // Level 32
        18247,      // Level 33
        20224,      // Level 34
        22406,      // Level 35
        24815,      // Level 36
        27473,      // Level 37
        30408,      // Level 38
        33648,      // Level 39
        37224,      // Level 40
        41171,      // Level 41
        45529,      // Level 42
        50339,      // Level 43
        55649,      // Level 44
        61512,      // Level 45
        67983,      // Level 46
        75127,      // Level 47
        83014,      // Level 48
        91721,      // Level 49
        101333,     // Level 50
        111945,     // Level 51
        123660,     // Level 52
        136594,     // Level 53
        150872,     // Level 54
        166636,     // Level 55
        184040,     // Level 56
        203254,     // Level 57
        224466,     // Level 58
        247886,     // Level 59
        273742,     // Level 60
        302288,     // Level 61
        333804,     // Level 62
        368599,     // Level 63
        407015,     // Level 64
        449428,     // Level 65
        496254,     // Level 66
        547953,     // Level 67
        605032,     // Level 68
        668051,     // Level 69
        737627,     // Level 70
        814445,     // Level 71
        899257,     // Level 72
        992895,     // Level 73
        1096278,    // Level 74
        1210421,    // Level 75
        1336443,    // Level 76
        1475581,    // Level 77
        1629200,    // Level 78
        1798808,    // Level 79
        1986068,    // Level 80
        2192818,    // Level 81
        2421087,    // Level 82
        2673114,    // Level 83
        2951373,    // Level 84
        3258594,    // Level 85
        3597792,    // Level 86
        3972294,    // Level 87
        4385776,    // Level 88
        4842295,    // Level 89
        5346332,    // Level 90
        5902831,    // Level 91
        6517253,    // Level 92
        7195629,    // Level 93
        7944614,    // Level 94
        8771558,    // Level 95
        9684577,    // Level 96
        10692629,   // Level 97
        11805606,   // Level 98
        13034431    // Level 99
    };
    
    /**
     * Maximum level in OSRS
     */
    public static final int MAX_LEVEL = 99;
    
    /**
     * Maximum XP in OSRS (200M)
     */
    public static final int MAX_XP = 200_000_000;
    
    /**
     * Gets the total XP required for a specific level.
     * @param level The level (1-99)
     * @return The total XP required for that level
     */
    public static int getXPForLevel(int level) {
        if (level < 1) return 0;
        if (level > MAX_LEVEL) return XP_TABLE[MAX_LEVEL - 1];
        return XP_TABLE[level - 1];
    }
    
    /**
     * Gets the level for a given amount of XP.
     * @param xp The total XP amount
     * @return The level (1-99)
     */
    public static int getLevelForXP(int xp) {
        if (xp < 0) return 1;
        
        // Binary search for efficiency
        int left = 0;
        int right = XP_TABLE.length - 1;
        int result = 1;
        
        while (left <= right) {
            int mid = left + (right - left) / 2;
            
            if (XP_TABLE[mid] <= xp) {
                result = mid + 1;
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        
        return Math.min(result, MAX_LEVEL);
    }
    
    /**
     * Gets the XP needed to reach the next level.
     * @param currentXP The current total XP
     * @return The XP needed for next level, or 0 if at max level
     */
    public static int getXPToNextLevel(int currentXP) {
        int currentLevel = getLevelForXP(currentXP);
        
        if (currentLevel >= MAX_LEVEL) {
            return 0; // Already at max level
        }
        
        int nextLevelXP = getXPForLevel(currentLevel + 1);
        return Math.max(0, nextLevelXP - currentXP);
    }
    
    /**
     * Calculates the percentage progress to the next level.
     * @param currentXP The current total XP
     * @return The percentage (0-100) of progress to next level
     */
    public static double getLevelProgress(int currentXP) {
        int currentLevel = getLevelForXP(currentXP);
        
        if (currentLevel >= MAX_LEVEL) {
            return 100.0; // Max level reached
        }
        
        int currentLevelXP = getXPForLevel(currentLevel);
        int nextLevelXP = getXPForLevel(currentLevel + 1);
        int xpInCurrentLevel = currentXP - currentLevelXP;
        int xpNeededForLevel = nextLevelXP - currentLevelXP;
        
        if (xpNeededForLevel <= 0) {
            return 0.0;
        }
        
        return (xpInCurrentLevel * 100.0) / xpNeededForLevel;
    }
    
    /**
     * Formats time in milliseconds to a readable string (HH:MM:SS).
     * @param millis The time in milliseconds
     * @return Formatted time string
     */
    public static String formatTime(long millis) {
        if (millis <= 0) {
            return "00:00:00";
        }
        
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        if (hours > 99) {
            return "99:59:59+"; // Cap display at 99 hours
        }
        
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }
    
    /**
     * Calculates time to next level based on XP per hour rate.
     * @param currentXP The current total XP
     * @param xpPerHour The XP per hour rate
     * @return Time to next level in milliseconds
     */
    public static long calculateTimeToLevel(int currentXP, int xpPerHour) {
        if (xpPerHour <= 0) {
            return 0; // Can't calculate with no XP gain
        }
        
        int xpNeeded = getXPToNextLevel(currentXP);
        if (xpNeeded <= 0) {
            return 0; // Already at max level or no XP needed
        }
        
        // Calculate hours needed and convert to milliseconds
        double hoursNeeded = xpNeeded / (double) xpPerHour;
        return (long) (hoursNeeded * 3600000);
    }
    
    /**
     * Gets the XP difference between two levels.
     * @param fromLevel The starting level
     * @param toLevel The target level
     * @return The XP difference
     */
    public static int getXPBetweenLevels(int fromLevel, int toLevel) {
        return getXPForLevel(toLevel) - getXPForLevel(fromLevel);
    }
}