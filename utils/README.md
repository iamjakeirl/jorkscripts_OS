# Utility Classes

This directory contains reusable utility classes that can be used across multiple scripts.

## Overview

Packages in this module:
- com.jork.utils (core helpers)
- com.jork.utils.chat (chatbox monitoring)
- com.jork.utils.metrics (metrics tracking and display)
- com.jork.utils.teleport (teleport handlers and destinations)
- com.jork.utils.tilepicker (tile selection UI)

## ScriptLogger

A standardized logging utility that provides consistent logging functionality for all scripts.
Supports global log-level filtering and helper methods for common script events.

Usage:

```java
import com.jork.utils.ScriptLogger;

// Basic logging
ScriptLogger.info(this, "Hello");
ScriptLogger.warning(this, "Careful");
ScriptLogger.error(this, "Failed");
ScriptLogger.debug(this, "Verbose");

// Global log level control
ScriptLogger.setMinLevel(ScriptLogger.Level.INFO);
ScriptLogger.setDebugEnabled(true);

// Specialized helpers
ScriptLogger.actionAttempt(this, "Walking to bank");
ScriptLogger.actionSuccess(this, "Reached bank");
ScriptLogger.actionFailure(this, "Failed to walk", 1, 3);
ScriptLogger.stateChange(this, oldState, newState, "Inventory full");
ScriptLogger.inventoryStatus(this, "Fish", fishCount, "Coins", coinCount);

// Exception logging
try {
    // risky work
} catch (Exception e) {
    ScriptLogger.exception(this, "perform risky operation", e);
}
```

## JorkBank

A comprehensive banking utility that wraps OSMB's Bank API with safe, verified operations.
Includes randomized timeouts and clear result enums for open/deposit/withdraw flows.

Key points:
- Uses `OpenResult` and `TransactionResult` for explicit outcome handling.
- Designed to work with OSMB's bank tab preference system.

Usage:

```java
import com.jork.utils.JorkBank;

JorkBank bank = new JorkBank(this);

if (bank.open() == JorkBank.OpenResult.SUCCESS) {
    bank.depositAllExcept(ECTOPHIAL, TELEPORT_ITEM);
    bank.withdraw(BONES, 8);
    bank.close();
}
```

## Navigation

Generic navigation helper with obstacle handling and a fast screen-tap movement path.

Key features:
- `navigateTo(...)` overloads for Area + obstacles + custom WalkConfig
- `simpleMoveTo(...)` for short-range screen-based walking with fallback

Usage:

```java
import com.jork.utils.Navigation;

Navigation nav = new Navigation(this);
nav.navigateTo(bankArea);

// Simple local movement (screen-tap if possible)
nav.simpleMoveTo(targetPos, 5000, 1);
```

## JorkTaps

Utility for repeated taps against a screen shape, area, or world position.
Useful for spam interactions with a timeout and optional human delay.

```java
import com.jork.utils.JorkTaps;

boolean success = JorkTaps.spamTapArea(
    this,
    area,
    100, 150, 400,
    15,
    () -> inventory.isFull(),
    true
);
```

## Tile Picker

Interactive tile selection UI.

Classes:
- `TilePickerPanel` for single tile selection
- `EnhancedTilePickerPanel` for multi-select and categories
- `TileCategory` and `TileSelection` for structured results

```java
import com.jork.utils.tilepicker.EnhancedTilePickerPanel;
import com.jork.utils.tilepicker.TileCategory;

Map<String, List<WorldPosition>> tiles = EnhancedTilePickerPanel.builder(this)
    .withCategory("traps", "Trap Tiles", Color.GREEN, 5)
    .withCategory("bait", "Bait Tiles", Color.ORANGE)
    .show();
```

## Chatbox Utilities

Chatbox monitoring utilities with pattern-based handlers and delay management.

Core classes:
- `ChatBoxListener` registers handlers and polls chatbox lines
- `ChatBoxMessage` provides matching helpers
- `HandlerRegistration` supports naming, counts, and removal
- `ChatBoxDelay` prevents false positives during overlays or taps

```java
import com.jork.utils.chat.ChatBoxListener;

chatListener = new ChatBoxListener(this)
    .on("you catch", msg -> fishCaught++)
    .named("fish-counter")
    .enableDebugLogging();
```

## Metrics System

Live metrics panel for scripts, including runtime and XP tracking.

Recommended entrypoint:
- Extend `AbstractMetricsScript` and register metrics in `onMetricsStart()`

Key components:
- `MetricsTracker` for registration and rendering
- `MetricsPanelConfig` for styling and layout
- `XPMetricProvider` and `RuntimeMetricProvider` for built-in metrics

```java
public class MyScript extends AbstractMetricsScript {
    public MyScript(Object core) { super(core); }

    @Override
    protected void onMetricsStart() {
        registerMetric("Trips", this::getTrips);
        enableXPTracking(SkillType.HUNTER);
    }
}
```

## Teleport System

Unified teleport handlers for items, spells, and walking fallbacks.

Core types:
- `TeleportHandler` interface
- `AbstractTeleportHandler` for item-based teleports
- `AbstractSpellTeleportHandler` for spellbook teleports
- `TeleportDestination` and `TeleportResult`
- `TeleportHandlerFactory` for ready-made handlers

Spell handlers:
- `VarrockTeleportHandler`
- `FaladorTeleportHandler`
- `CamelotTeleportHandler`
- `LumbridgeTeleportHandler`
- `ArdougneTeleportHandler`
- `TrollheimTeleportHandler`
- `KourendCastleTeleportHandler`
- `WatchtowerTeleportHandler`
- `ApeAtollTeleportHandler`

Item handlers:
- `RingOfDuelingHandler`
- `EctophialHandler`
- `DrakansMedallionHandler`
- `AmuletOfTheEyeHandler`

Walking handler:
- `WalkingHandler`

```java
TeleportHandler handler = TeleportHandlerFactory.createRingOfDuelingHandler(this);
if (handler.canTeleport()) {
    TeleportResult result = handler.teleport();
    if (result.isSuccess()) {
        WorldPosition target = handler.getDestination().getWalkTarget();
        getWalker().walkTo(target);
    }
}
```

## Debug and Exceptions

- `DebugDrawer` draws a visible marker for Z-offset calibration
- `ExceptionUtils` rethrows task-interrupt exceptions to respect OSMB task flow
