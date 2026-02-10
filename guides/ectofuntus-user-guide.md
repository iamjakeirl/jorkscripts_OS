# jorkTofuntus User Guide

## Overview
Automates Prayer training / token getting at the Ectofuntus. The script banks, collects slime, grinds bones, and worships at the altar in a loop.

## Requirements
- Ectophial in your bank or inventory
- Bones (whichever type you select)
- Empty pots and empty buckets
- If using a spell teleport to bank: the correct spellbook and runes (or a rune pouch)

## Getting Started
1. Copy the JAR into your OSMB scripts folder.
2. Place all needed items into a single tab, all bones, runes for TP (if not rune pouch mode), ectophial, buckets, pots, slime. recommend having at least 18 empty buckets and pots. 
3. Start the script and pick your bone type and banking method. Mixed bone mode is on by default — it uses all bone types in the selected tab. The one set in the dropdown is used first.
4. Adjust any options you want, then click **Start Training**.

## Banking Methods
- **Varrock Teleport** / **Grand Exchange Teleport** / **Falador Teleport** / **Camelot Teleport** — teleports to a bank, withdraws 8 of each supply per trip.
- **Walk to Port Phasmatys** — walks to the nearby bank instead of teleporting. Withdraws 9 of each supply per trip since the walk doesnt require 3 rune slots. **You must be wearing a Ghostspeak amulet** to use the bank in Port Phasmatys.

## Options
- **Use all bone types in tab** (default: on) — uses any supported bone type found in your bank tab instead of just one. The type set in the dropdown is prioritized first.
- **Enable rune pouch mode** — tells the script your runes are in a rune pouch so it won't try to withdraw them separately.
- **Token collection window** (default: 100–200) — the script collects tokens in randomized batches within this range.
- **Enable debug logging** — shows extra detail in the log if you need to troubleshoot.

## Failsafe
- **Stop if no XP gained** (default: 10 minutes) — automatically stops the script if you haven't gained Prayer XP within the time you set (1–60 min).
- **Pause timer during breaks/hops** — prevents the failsafe from triggering while you're logged out for a break or world hop.

## Tips
- 58+ Agility lets the script use a shortcut in the slime dungeon, speeding up runs.
- If you close the config window without pressing start, defaults are used (mixed bones, 10 min failsafe, 100–200 token window).
- The script detects where you are on startup and recovers automatically. Theoretically if you start this near any bank it should work. 

## Troubleshooting
- **Stops saying missing ectophial** — make sure you have an Ectophial in your bank or inventory.
- **Stops saying missing pots/buckets** — restock empty pots and empty buckets.
- **Spell teleport issues** — check you're on the right spellbook and have the runes.
- **Rune pouch errors** — make sure the rune pouch is in your inventory before starting.
- **No bones found** — stock the bone type you selected, or enable "use all bone types" if using mixed bones.
