package com.jork.script.WineCollector.tasks;

import com.osmb.api.location.position.types.WorldPosition;
import com.osmb.api.scene.RSObject;
import com.osmb.api.utils.RandomUtils;
import com.jork.utils.Navigation;
import com.jork.utils.ScriptLogger;
import com.jork.script.WineCollector.WineCollector;
import com.jork.script.WineCollector.config.WineConfig;

import java.util.List;

public class NavigateTask implements Task {

    private final WineCollector script;
    private final Navigation navigation;

    public NavigateTask(WineCollector script) {
        this.script = script;
        this.navigation = new Navigation(script);
    }

    @Override
    public boolean canExecute() {
        WorldPosition currentPos = script.getWorldPosition();
        if (currentPos == null) {
            return false;
        }

        boolean shouldBank = script.shouldBank();
        int currentPlane = currentPos.getPlane();

        if (shouldBank) {
            // Need to navigate to bank (ground floor, bank area)
            if (currentPlane != WineConfig.GROUND_FLOOR_PLANE) {
                return true;  // Need to descend
            }
            if (!WineConfig.BANK_AREA.contains(currentPos)) {
                return true;  // On ground floor but not at bank
            }
            return false;  // Already at bank
        } else {
            // Need to navigate to collection area (top floor)
            if (currentPlane != WineConfig.TOP_FLOOR_PLANE) {
                return true;  // Need to ascend
            }
            if (!WineConfig.UPSTAIRS_AREA.contains(currentPos)) {
                return true;  // On top floor but not in collection area
            }
            return false;  // Already at collection area
        }
    }

    @Override
    public int execute() {
        WorldPosition currentPos = script.getWorldPosition();
        if (currentPos == null) {
            return WineConfig.POLL_DELAY_LONG;
        }

        int currentPlane = currentPos.getPlane();
        boolean shouldBank = script.shouldBank();

        if (shouldBank) {
            return navigateToBank(currentPos, currentPlane);
        } else {
            return navigateToCollection(currentPos, currentPlane);
        }
    }

    /**
     * Navigates to the bank from any location.
     * Handles descending from top floor → 2nd floor → ground floor → bank area.
     */
    private int navigateToBank(WorldPosition currentPos, int currentPlane) {
        // Already on ground floor - navigate to bank area
        if (currentPlane == WineConfig.GROUND_FLOOR_PLANE) {
            if (!WineConfig.BANK_AREA.contains(currentPos)) {
                ScriptLogger.navigation(script, "Walking to bank area");
                boolean navigating = navigation.navigateTo(WineConfig.BANK_AREA);
                if (navigating) {
                    // Add human delay after arriving at bank area
                    int arrivalDelay = RandomUtils.weightedRandom(
                        WineConfig.ARRIVAL_DELAY - 100,
                        WineConfig.ARRIVAL_DELAY + 100
                    );
                    script.pollFramesHuman(() -> true, arrivalDelay);
                }
                return navigating ? WineConfig.POLL_DELAY_MEDIUM : WineConfig.POLL_DELAY_LONG;
            }
            return WineConfig.POLL_DELAY_MEDIUM;  // At bank, ready for BankTask
        }

        // On top floor - descend to 2nd floor
        if (currentPlane == WineConfig.TOP_FLOOR_PLANE) {
            List<RSObject> ladders = script.getObjectManager().getObjects(obj ->
                obj != null && obj.getName() != null &&
                obj.getName().equals(WineConfig.LADDER_NAME) &&
                obj.canReach()
            );

            if (ladders == null || ladders.isEmpty()) {
                return WineConfig.POLL_DELAY_LONG;
            }

            // Find furthest ladder (better pathing)
            ladders.sort((a, b) ->
                Double.compare(
                    b.distance(currentPos),
                    a.distance(currentPos)
                )
            );

            RSObject furthestLadder = ladders.get(0);
            ScriptLogger.navigation(script, "Climbing down from top floor");

            boolean interacted = furthestLadder.interact(WineConfig.LADDER_DOWN_ACTION);
            if (!interacted) {
                return WineConfig.POLL_DELAY_LONG;
            }

            int climbTimeout = RandomUtils.weightedRandom(
                WineConfig.LADDER_CLIMB_TIMEOUT - 100,
                WineConfig.LADDER_CLIMB_TIMEOUT + 100
            );
            script.pollFramesHuman(() -> {
                WorldPosition pos = script.getWorldPosition();
                return pos != null && pos.getPlane() == WineConfig.SECOND_FLOOR_PLANE;
            }, climbTimeout);

            return WineConfig.POLL_DELAY_MEDIUM;
        }

        // On 2nd floor - descend to ground floor
        if (currentPlane == WineConfig.SECOND_FLOOR_PLANE) {
            if (!WineConfig.LADDER_AREA_SECOND_FLOOR.contains(currentPos)) {
                ScriptLogger.navigation(script, "Walking to ladder area on 2nd floor");
                boolean navigating = navigation.navigateTo(WineConfig.LADDER_AREA_SECOND_FLOOR);
                if (navigating) {
                    // Add human delay after arriving at ladder area
                    int arrivalDelay = RandomUtils.weightedRandom(
                        WineConfig.ARRIVAL_DELAY - 100,
                        WineConfig.ARRIVAL_DELAY + 100
                    );
                    script.pollFramesHuman(() -> true, arrivalDelay);
                }
                return navigating ? WineConfig.POLL_DELAY_MEDIUM : WineConfig.POLL_DELAY_LONG;
            }

            List<RSObject> ladders = script.getObjectManager().getObjects(obj ->
                obj != null && obj.getName() != null &&
                obj.getName().equals(WineConfig.LADDER_NAME) &&
                obj.canReach()
            );

            if (ladders == null || ladders.isEmpty()) {
                return WineConfig.POLL_DELAY_LONG;
            }

            // Find furthest ladder
            ladders.sort((a, b) ->
                Double.compare(
                    b.distance(currentPos),
                    a.distance(currentPos)
                )
            );

            RSObject ladder = ladders.get(0);
            ScriptLogger.navigation(script, "Climbing down to ground floor");

            boolean interacted = ladder.interact(WineConfig.LADDER_DOWN_ACTION);
            if (!interacted) {
                return WineConfig.POLL_DELAY_LONG;
            }

            int climbTimeout = RandomUtils.weightedRandom(
                WineConfig.LADDER_CLIMB_TIMEOUT - 100,
                WineConfig.LADDER_CLIMB_TIMEOUT + 100
            );
            script.pollFramesHuman(() -> {
                WorldPosition pos = script.getWorldPosition();
                return pos != null && pos.getPlane() == WineConfig.GROUND_FLOOR_PLANE;
            }, climbTimeout);

            return WineConfig.POLL_DELAY_MEDIUM;
        }

        return WineConfig.POLL_DELAY_LONG;
    }

    /**
     * Navigates to the collection area from any location.
     * Handles ascending from ground floor → 2nd floor → top floor.
     */
    private int navigateToCollection(WorldPosition currentPos, int currentPlane) {
        // On ground floor - ascend to 2nd floor
        if (currentPlane == WineConfig.GROUND_FLOOR_PLANE) {
            if (!WineConfig.LADDER_AREA.contains(currentPos)) {
                ScriptLogger.navigation(script, "Walking to ladder area");
                boolean navigating = navigation.navigateTo(WineConfig.LADDER_AREA);
                if (navigating) {
                    // Add human delay after arriving at ladder area
                    int arrivalDelay = RandomUtils.weightedRandom(
                        WineConfig.ARRIVAL_DELAY - 100,
                        WineConfig.ARRIVAL_DELAY + 100
                    );
                    script.pollFramesHuman(() -> true, arrivalDelay);
                }
                return navigating ? WineConfig.POLL_DELAY_MEDIUM : WineConfig.POLL_DELAY_LONG;
            }

            RSObject ladder = script.getObjectManager().getClosestObject(currentPos, WineConfig.LADDER_NAME);

            if (ladder == null) {
                return WineConfig.POLL_DELAY_LONG;
            }

            ScriptLogger.navigation(script, "Climbing up to 2nd floor");

            boolean interacted = ladder.interact(WineConfig.LADDER_UP_ACTION);
            if (!interacted) {
                return WineConfig.POLL_DELAY_LONG;
            }

            int climbTimeout = RandomUtils.weightedRandom(
                WineConfig.LADDER_CLIMB_TIMEOUT - 100,
                WineConfig.LADDER_CLIMB_TIMEOUT + 100
            );
            script.pollFramesHuman(() -> {
                WorldPosition pos = script.getWorldPosition();
                return pos != null && pos.getPlane() == WineConfig.SECOND_FLOOR_PLANE;
            }, climbTimeout);

            return WineConfig.POLL_DELAY_MEDIUM;
        }

        // On 2nd floor - ascend to top floor
        if (currentPlane == WineConfig.SECOND_FLOOR_PLANE) {
            List<RSObject> ladders = script.getObjectManager().getObjects(obj ->
                obj != null && obj.getName() != null &&
                obj.getName().equals(WineConfig.LADDER_NAME) &&
                obj.canReach()
            );

            if (ladders == null || ladders.isEmpty()) {
                return WineConfig.POLL_DELAY_LONG;
            }

            // Find furthest ladder
            ladders.sort((a, b) ->
                Double.compare(
                    b.distance(currentPos),
                    a.distance(currentPos)
                )
            );

            RSObject furthestLadder = ladders.get(0);
            ScriptLogger.navigation(script, "Climbing up to top floor");

            boolean interacted = furthestLadder.interact(WineConfig.LADDER_UP_ACTION);
            if (!interacted) {
                return WineConfig.POLL_DELAY_LONG;
            }

            int climbTimeout = RandomUtils.weightedRandom(
                WineConfig.LADDER_CLIMB_TIMEOUT - 100,
                WineConfig.LADDER_CLIMB_TIMEOUT + 100
            );
            script.pollFramesHuman(() -> {
                WorldPosition pos = script.getWorldPosition();
                return pos != null && pos.getPlane() == WineConfig.TOP_FLOOR_PLANE;
            }, climbTimeout);

            return WineConfig.POLL_DELAY_MEDIUM;
        }

        // Already on top floor
        if (currentPlane == WineConfig.TOP_FLOOR_PLANE) {
            if (!WineConfig.UPSTAIRS_AREA.contains(currentPos)) {
                ScriptLogger.navigation(script, "Walking to collection area");
                boolean navigating = navigation.navigateTo(WineConfig.UPSTAIRS_AREA);
                if (navigating) {
                    // Add human delay after arriving at collection area
                    int arrivalDelay = RandomUtils.weightedRandom(
                        WineConfig.ARRIVAL_DELAY - 100,
                        WineConfig.ARRIVAL_DELAY + 100
                    );
                    script.pollFramesHuman(() -> true, arrivalDelay);
                }
                return navigating ? WineConfig.POLL_DELAY_MEDIUM : WineConfig.POLL_DELAY_LONG;
            }
            return WineConfig.POLL_DELAY_MEDIUM;  // At collection area, ready for CollectTask
        }

        return WineConfig.POLL_DELAY_LONG;
    }
}
