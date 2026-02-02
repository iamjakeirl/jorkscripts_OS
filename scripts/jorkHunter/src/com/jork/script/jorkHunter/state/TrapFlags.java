package com.jork.script.jorkHunter.state;

import java.util.EnumSet;
import java.util.Set;
import java.util.Optional;

/**
 * Container for managing multiple TrapFlag values for a single trap.
 * Provides methods for flag manipulation and priority resolution.
 */
public class TrapFlags {
    private final EnumSet<TrapFlag> flags;
    
    public TrapFlags() {
        this.flags = EnumSet.noneOf(TrapFlag.class);
    }
    
    public TrapFlags(TrapFlag... initialFlags) {
        this.flags = EnumSet.noneOf(TrapFlag.class);
        for (TrapFlag flag : initialFlags) {
            flags.add(flag);
        }
    }
    
    public TrapFlags(EnumSet<TrapFlag> flags) {
        this.flags = flags.clone();
    }
    
    /**
     * Add a flag to this container.
     */
    public void addFlag(TrapFlag flag) {
        flags.add(flag);
    }
    
    /**
     * Remove a flag from this container.
     */
    public void removeFlag(TrapFlag flag) {
        flags.remove(flag);
    }
    
    /**
     * Clear all flags.
     */
    public void clearFlags() {
        flags.clear();
    }
    
    /**
     * Check if a specific flag is set.
     */
    public boolean hasFlag(TrapFlag flag) {
        return flags.contains(flag);
    }
    
    /**
     * Check if any flags are set.
     */
    public boolean hasAnyFlags() {
        return !flags.isEmpty();
    }
    
    /**
     * Get the highest priority flag (lowest ordinal).
     * @return Optional containing the highest priority flag, or empty if no flags are set
     */
    public Optional<TrapFlag> getHighestPriorityFlag() {
        return flags.stream()
            .min((a, b) -> Integer.compare(a.ordinal(), b.ordinal()));
    }
    
    /**
     * Check if there are conflicting flags that shouldn't be set simultaneously.
     * For example, LAYING_IN_PROGRESS shouldn't be set with READY_FOR_REMOVAL.
     */
    public boolean hasConflictingFlags() {
        // Can't be laying and ready for removal at the same time
        if (hasFlag(TrapFlag.LAYING_IN_PROGRESS) && hasFlag(TrapFlag.READY_FOR_REMOVAL)) {
            return true;
        }
        
        // Can't be laying and need interaction at the same time
        if (hasFlag(TrapFlag.LAYING_IN_PROGRESS) && hasFlag(TrapFlag.NEEDS_INTERACTION)) {
            return true;
        }
        
        // Can't be ready for removal and need interaction (collapsed) at the same time
        if (hasFlag(TrapFlag.READY_FOR_REMOVAL) && hasFlag(TrapFlag.NEEDS_INTERACTION)) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Get a copy of all flags currently set.
     */
    public Set<TrapFlag> getFlags() {
        return EnumSet.copyOf(flags);
    }
    
    /**
     * Create a copy of this TrapFlags instance.
     */
    public TrapFlags copy() {
        return new TrapFlags(flags);
    }
    
    /**
     * Set flags from another TrapFlags instance.
     */
    public void setFlags(TrapFlags other) {
        this.flags.clear();
        this.flags.addAll(other.flags);
    }
    
    @Override
    public String toString() {
        if (flags.isEmpty()) {
            return "TrapFlags[]";
        }
        return "TrapFlags" + flags;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        TrapFlags other = (TrapFlags) obj;
        return flags.equals(other.flags);
    }
    
    @Override
    public int hashCode() {
        return flags.hashCode();
    }
}