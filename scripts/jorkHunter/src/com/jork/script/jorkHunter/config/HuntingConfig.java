package com.jork.script.jorkHunter.config;

import com.jork.script.jorkHunter.trap.TrapType;
import com.jork.utils.ScriptLogger;
import com.osmb.api.script.Script;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Configuration manager for jorkHunter script variants.
 * Loads configuration from embedded properties file to determine which hunting types are enabled.
 */
public class HuntingConfig {
    private static final String CONFIG_FILE = "/hunting-config.properties";
    private static final String DEFAULT_CONFIG_FILE = "/default-hunting-config.properties";
    
    private final Set<TrapType> enabledTypes;
    private final String scriptName;
    private final String scriptDescription;
    private final String variantName;
    private final boolean allFeaturesEnabled;
    
    private HuntingConfig(Set<TrapType> enabledTypes, String scriptName, 
                         String scriptDescription, String variantName, boolean allFeaturesEnabled) {
        this.enabledTypes = enabledTypes;
        this.scriptName = scriptName;
        this.scriptDescription = scriptDescription;
        this.variantName = variantName;
        this.allFeaturesEnabled = allFeaturesEnabled;
    }
    
    /**
     * Loads the hunting configuration from embedded properties file.
     * Falls back to default config if specific config not found.
     */
    public static HuntingConfig load(Script script) {
        Properties props = new Properties();
        
        // Try to load specific configuration first
        InputStream configStream = HuntingConfig.class.getResourceAsStream(CONFIG_FILE);
        if (configStream == null) {
            // Fall back to default configuration
            configStream = HuntingConfig.class.getResourceAsStream(DEFAULT_CONFIG_FILE);
            if (configStream == null) {
                // If no config files found, enable all features (development mode)
                ScriptLogger.warning(script, "No configuration files found, enabling all features");
                return createAllFeaturesConfig();
            }
        }
        
        try {
            props.load(configStream);
            return parseConfig(script, props);
        } catch (IOException e) {
            ScriptLogger.exception(script, "loading hunting configuration", e);
            // Fall back to all features enabled
            return createAllFeaturesConfig();
        } finally {
            try {
                configStream.close();
            } catch (IOException e) {
                // Ignore close errors
            }
        }
    }
    
    /**
     * Creates a configuration with all features enabled (for development).
     */
    private static HuntingConfig createAllFeaturesConfig() {
        Set<TrapType> allTypes = EnumSet.allOf(TrapType.class);
        return new HuntingConfig(
            allTypes,
            "jorkHunter",
            "A versatile All-In-One (AIO) hunter script.",
            "All Features",
            true
        );
    }
    
    /**
     * Parses configuration from properties.
     */
    private static HuntingConfig parseConfig(Script script, Properties props) {
        String scriptName = props.getProperty("script.name", "jorkHunter");
        String scriptDescription = props.getProperty("script.description", 
            "A versatile All-In-One (AIO) hunter script.");
        String variantName = props.getProperty("variant.name", "Default");
        
        // Parse enabled trap types
        String enabledTypesStr = props.getProperty("enabled.types", "ALL");
        Set<TrapType> enabledTypes;
        boolean allFeatures = false;
        
        if ("ALL".equalsIgnoreCase(enabledTypesStr)) {
            enabledTypes = EnumSet.allOf(TrapType.class);
            allFeatures = true;
        } else {
            enabledTypes = EnumSet.noneOf(TrapType.class);
            String[] typeNames = enabledTypesStr.split(",");
            for (String typeName : typeNames) {
                try {
                    TrapType type = TrapType.valueOf(typeName.trim().toUpperCase());
                    enabledTypes.add(type);
                } catch (IllegalArgumentException e) {
                    ScriptLogger.warning(script, "Unknown trap type in config: " + typeName);
                }
            }
        }
        
        if (enabledTypes.isEmpty()) {
            ScriptLogger.warning(script, "No valid trap types in configuration, defaulting to BIRD_SNARE");
            enabledTypes.add(TrapType.BIRD_SNARE);
        }
        
        ScriptLogger.info(script, "Loaded hunting config: " + variantName + 
                         " with types: " + enabledTypes);
        
        return new HuntingConfig(enabledTypes, scriptName, scriptDescription, variantName, allFeatures);
    }
    
    // Getters
    public Set<TrapType> getEnabledTypes() {
        return Collections.unmodifiableSet(enabledTypes);
    }
    
    public boolean isTypeEnabled(TrapType type) {
        return enabledTypes.contains(type);
    }
    
    public String getScriptName() {
        return scriptName;
    }
    
    public String getScriptDescription() {
        return scriptDescription;
    }
    
    public String getVariantName() {
        return variantName;
    }
    
    public boolean isAllFeaturesEnabled() {
        return allFeaturesEnabled;
    }
    
    /**
     * Gets locations that are valid for the enabled trap types.
     * This can be extended to filter locations based on trap type requirements.
     */
    public boolean isLocationValidForConfig(String locationName) {
        // For now, all locations work with all trap types
        // This can be extended later to have trap-type-specific locations
        return true;
    }
}