# Ectofuntus Script - In-Game Verification Checklist

All values below need to be verified in-game. Update `EctofuntusConstants.java` with the correct values.

---

## Item IDs

These should be correct (from OSMB ItemID constants), but verify if issues occur:

| Item | Current ID | Status |
|------|------------|--------|
| Empty Bucket | 1925 | ⬜ Verify |
| Bucket of Slime | 4286 | ⬜ Verify |
| Empty Pot | 1931 | ⬜ Verify |
| Bonemeal | 1854 | ⬜ Verify |
| Ectophial (full) | 4251 | ⬜ Verify |
| Ectophial (empty) | 4252 | ⬜ Verify |

---

## Region & Plane Values

| Location | Current Value | Needed Data | Status |
|----------|---------------|-------------|--------|
| Ectofuntus Region ID | 14646 | Region ID when standing at Ectofuntus | ⬜ Verify |
| Basement Plane | 0 | Plane value when at Pool of Slime | ⬜ Verify |
| Altar Plane | 0 | Plane value when at Ectofuntus altar | ⬜ Verify |
| Grinder Plane | 2 | Plane value when at bone grinder (top floor) | ⬜ Verify |

**How to get Region ID:** Use developer tools or `getWorldPosition().getRegionID()`

**How to get Plane:** Use `getWorldPosition().getPlane()`

---

## Object Names

Stand near each object and check its exact name (case-sensitive):

| Object | Current Name | Location | Status |
|--------|--------------|----------|--------|
| Pool of Slime | `"Pool of Slime"` | Basement - fill buckets here | ⬜ Verify |
| Ectofuntus Altar | `"Ectofuntus"` | Ground floor - worship here | ⬜ Verify |
| Bone Hopper/Loader | `"Loader"` | Top floor - use bones on this | ⬜ Verify |
| Grinder | `"Grinder"` | Top floor - may not be needed if hopper auto-grinds | ⬜ Verify |
| Bonemeal Bin | `"Bin"` | Top floor - may not be needed if bonemeal goes to inventory | ⬜ Verify |
| Staircase | `"Staircase"` | Various floors | ⬜ Verify |
| Trapdoor | `"Trapdoor"` | If basement uses trapdoor instead of stairs | ⬜ Verify |

---

## Interaction Actions

Right-click each object to see available actions (case-sensitive):

| Object | Current Action | Alternative Actions to Check | Status |
|--------|----------------|------------------------------|--------|
| Pool of Slime | `"Fill"` | "Use", default click | ⬜ Verify |
| Ectofuntus Altar | `"Worship"` | "Pray", default click | ⬜ Verify |
| Bone Hopper | Use item on it | May have "Use" or default action | ⬜ Verify |
| Grinder | `"Grind"` | "Operate", "Wind", default click | ⬜ Verify |
| Bonemeal Bin | `"Empty"` | "Collect", default click | ⬜ Verify |
| Stairs (up) | `"Climb-up"` | "Climb up", "Go-up" | ⬜ Verify |
| Stairs (down) | `"Climb-down"` | "Climb down", "Go-down" | ⬜ Verify |

---

## World Positions

Stand at each location and record the exact coordinates:

### Pool of Slime (Basement)
| Field | Current Value | Actual Value | Status |
|-------|---------------|--------------|--------|
| X | 3682 | | ⬜ Record |
| Y | 9888 | | ⬜ Record |
| Plane | 0 | | ⬜ Record |

### Ectofuntus Altar
| Field | Current Value | Actual Value | Status |
|-------|---------------|--------------|--------|
| X | 3659 | | ⬜ Record |
| Y | 3517 | | ⬜ Record |
| Plane | 0 | | ⬜ Record |

### Bone Hopper (Top Floor)
| Field | Current Value | Actual Value | Status |
|-------|---------------|--------------|--------|
| X | 3659 | | ⬜ Record |
| Y | 3524 | | ⬜ Record |
| Plane | 2 | | ⬜ Record |

### Grinder (Top Floor)
| Field | Current Value | Actual Value | Status |
|-------|---------------|--------------|--------|
| X | 3659 | | ⬜ Record |
| Y | 3523 | | ⬜ Record |
| Plane | 2 | | ⬜ Record |

### Bonemeal Bin (Top Floor)
| Field | Current Value | Actual Value | Status |
|-------|---------------|--------------|--------|
| X | 3658 | | ⬜ Record |
| Y | 3524 | | ⬜ Record |
| Plane | 2 | | ⬜ Record |

### Stairs: Ground Floor to Basement
| Field | Current Value | Actual Value | Status |
|-------|---------------|--------------|--------|
| X | 3669 | | ⬜ Record |
| Y | 3519 | | ⬜ Record |
| Plane | 0 | | ⬜ Record |

### Stairs: Basement to Ground Floor
| Field | Current Value | Actual Value | Status |
|-------|---------------|--------------|--------|
| X | 3669 | | ⬜ Record |
| Y | 9888 | | ⬜ Record |
| Plane | 0 | | ⬜ Record |

### Stairs: Ground Floor to Top Floor
| Field | Current Value | Actual Value | Status |
|-------|---------------|--------------|--------|
| X | 3667 | | ⬜ Record |
| Y | 3520 | | ⬜ Record |
| Plane | 0 | | ⬜ Record |

### Stairs: Top Floor to Ground Floor
| Field | Current Value | Actual Value | Status |
|-------|---------------|--------------|--------|
| X | 3667 | | ⬜ Record |
| Y | 3520 | | ⬜ Record |
| Plane | 2 | | ⬜ Record |

---

## Gameplay Mechanics to Verify

### Slime Collection
- [ ] Does clicking Pool of Slime fill ALL empty buckets at once?
- [ ] Or do you need to click multiple times / use bucket on pool?
- [ ] Does the ectophial auto-refill when using the pool, or separate action?

### Bone Grinding
- [ ] Using bones on hopper - does it grind ALL bones automatically?
- [ ] Or do you need to operate the grinder separately?
- [ ] Does bonemeal go directly to inventory, or need to collect from bin?
- [ ] How long does grinding take per bone (for timeout estimation)?

### Worship
- [ ] Does one worship action consume one bonemeal + one slime?
- [ ] Or does it consume multiple per action?
- [ ] Any animation/delay between worship actions?

### Navigation
- [ ] Are there multiple staircases? Which ones to use?
- [ ] Is there a trapdoor to basement or just stairs?
- [ ] Any obstacles or doors that need opening?

---

## Test Mode Settings

The script has test mode enabled. To test individual components:

1. Open `Ectofuntus.java`
2. Find `TEST_MODE_ENABLED` (line ~52) - set to `true`
3. Find `CURRENT_TEST` (line ~62) - set to one of:
   - `TestTarget.BANK_TASK`
   - `TestTarget.TELEPORT_TASK`
   - `TestTarget.SLIME_TASK`
   - `TestTarget.GRIND_TASK`
   - `TestTarget.WORSHIP_TASK`

---

## Quick Reference: File to Update

All constants are in:
```
scripts/Ectofuntus/src/com/jork/script/Ectofuntus/EctofuntusConstants.java
```

After updating, rebuild with:
```bash
./gradlew :scripts:Ectofuntus:build
```
