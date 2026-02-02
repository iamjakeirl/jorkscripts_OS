# Utility Classes

This directory contains reusable utility classes that can be used across multiple scripts.

## ScriptLogger

A standardized logging utility that provides consistent logging functionality for all scripts.

### Usage

```java
// Import the utility
import com.jork.utils.ScriptLogger;

// Basic logging methods
ScriptLogger.info(this, "Your informational message");
ScriptLogger.warning(this, "Your warning message");
ScriptLogger.error(this, "Your error message");
ScriptLogger.debug(this, "Your debug message");

// Specialized logging methods
ScriptLogger.actionAttempt(this, "Walking to bank");
ScriptLogger.actionSuccess(this, "Reached bank successfully");
ScriptLogger.actionFailure(this, "Failed to reach bank", 1, 3);
ScriptLogger.navigation(this, "lumbridge bank");

// State change logging
ScriptLogger.stateChange(this, oldState, newState, "Inventory full");

// Inventory status logging
ScriptLogger.inventoryStatus(this, "Fish", fishCount, "Coins", coinCount);

// Script lifecycle logging
ScriptLogger.startup(this, "1.0", "jork", "Fishing");
ScriptLogger.shutdown(this, "User stopped script");

// Exception logging
try {
    // Some risky operation
} catch (Exception e) {
    ScriptLogger.exception(this, "perform risky operation", e);
}

// Custom logging
ScriptLogger.custom(this, "TRACE", "Custom trace message");
```

### Benefits

1. **Consistency**: All scripts use the same logging format
2. **Maintainability**: Changes to logging behavior only need to be made in one place
3. **Reusability**: Write once, use in all scripts
4. **Type Safety**: Method signatures prevent common logging mistakes
5. **Rich Context**: Automatic class name prefixing and categorization

### Migration from Old Logging

Replace old logging patterns:

```java
// Old way
private void logInfo(String message) {
    log(CLASS_NAME, "[INFO] " + message);
}
logInfo("Some message");

// New way
ScriptLogger.info(this, "Some message");
```

### Adding New Scripts

When creating new scripts, simply:

1. Import: `import com.jork.utils.ScriptLogger;`
2. Use the static methods as shown above
3. No need to create logging methods in each script class 