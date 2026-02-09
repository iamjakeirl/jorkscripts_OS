package com.jork.script.jorkHunter.utils.tasks;

public interface Task {
    /**
     * Determines if this task should be executed.
     * @return true if the task should execute, false otherwise.
     */
    boolean canExecute();

    /**
     * The main logic of the task.
     * @return The delay in milliseconds for the next poll.
     */
    int execute();

    /**
     * Resets any mid-action execution state (e.g., committed positions, movement flags).
     * Called on relog/logout to prevent stale state from causing stuck loops.
     */
    default void resetExecutionState() {}
} 