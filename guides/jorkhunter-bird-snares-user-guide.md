# JorkHunter – Bird Snares User Guide

## What This Script Does
- Hunts birds using **bird snares**.
- Places traps in a selected pattern around a tile you choose in-game.
- Checks, replaces, and re-lays traps automatically.
- Tracks catches, misses, and XP with an on-screen metrics panel.

## Creature Selection
Available targets::
- `Crimson Swift`
- `Copper Longtail`
- `Tropical Wagtail`
- `Cerulean Twitch`

## Requirements
- Bird snares in inventory (I reccomend a large amount, 1000+ at least)
- Hunter level appropriate for your target.


## Setup
1. Load the script into OSMB scripts folder (you can open the scripts folder from the menu bar in OSMB)
2. Make sure hunter helpers / respawn circles are turned on. 
3. Make sure your xp drop settings are set to permanent duration, skill on most recent or hunter. 
3. Choose your **Creature**, **Pattern**, and any **Advanced Options**.
4. Click **Start Hunting**.
5. The script will prompt you in-game to select a tile as the anchor/central point for your pattern (or each tile you'd like to place a trap on in `Custom`).


## Tile Selection (Required)
This script always uses the in-game tile picker:
- For **`Custom`** pattern: you select **multiple tiles** to use as exact trap positions.
- For all other patterns: you select **one anchor tile** and the script places traps around it.
- Make sure not to close out of this without selecting. It will work with a default placement mode near your position just in case, but you should select a tile.


## Placement Patterns
You select a placement pattern in the configuration window. The script places traps based on your Hunter level and the pattern rules below.

- `Auto`
  Automatically chooses a pattern based on trap count: 1–2 traps = Line, 3 traps = L-Pattern, 4 traps = Cross, 5 traps = X-Pattern.
- `X-Pattern`
  Diagonal X shape around your anchor tile.
- `L-Pattern`
  L shape with the anchor as the corner.
- `Line`
  A straight line through the anchor tile. You can choose `Horizontal` or `Vertical`.
- `Cross`
  Cardinal directions (N, S, E, W), center included only when 5 traps are available.
- `Custom`
  Uses the exact tiles you select (multi-tile picker).

## Trap Count by Hunter Level
The script automatically sets max traps based on your Hunter level:
- Levels 1–19: 1 trap
- Levels 20–39: 2 traps
- Levels 40–59: 3 traps
- Levels 60–79: 4 traps
- Levels 80+: 5 traps

You can override this with **“Enter hunter level manually”** in Advanced Options. If you have any trouble with number of traps, try setting this manually. 

## Advanced Options
- `Enter hunter level manually`
  Lets you override automatic trap count detection.
- `Expedite trap collection before breaks`
  When a break or hop is due, the script can fast-clear traps first or wait for them all to expire naturally and simply stop placing new ones.
- `Chance %`
  Sets the chance that expedited collection triggers - sometimes go fast, sometimes wait for natural expiration.
- `Enable debug logging`
  Adds extra logs for troubleshooting.
- `Stop script if no XP gained for:`
  XP failsafe. Stops the script if Hunter XP doesn’t change for the selected time. If you are using WDH (which i reccomend) i'd set this to at least 10, maybe 15
  if you happen to get unlucky with hops, occasionally a good amount of time will pass without xp gain. 
- `Pause timer during breaks/hops`
  Prevents false failsafe triggers during logout. I'd leave this checked unless you have a reason not to. 

## Inventory Handling
- The script **keeps feathers**.
- It **drops raw bird meat and bones** when your inventory is low on space. 

## Troubleshooting
- **Script stops due to failsafe**: If behavior looks normal but script still stops, increase the XP timeout.
- **Script doesn't pick up traps**: make sure hunter helper / trap respawn circles / timers are enabled. We need those. 
