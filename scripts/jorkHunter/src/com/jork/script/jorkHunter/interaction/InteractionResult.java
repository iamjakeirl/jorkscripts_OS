package com.jork.script.jorkHunter.interaction;

import com.osmb.api.location.position.types.WorldPosition;
import java.util.Optional;

/**
 * Result of a trap interaction attempt, containing success status and metadata.
 */
public record InteractionResult(
    boolean success,
    InteractionType type,
    String message,
    WorldPosition position,
    Optional<Exception> error
) {
    public enum InteractionType {
        TRAP_LAID,           // Successfully laid a new trap
        TRAP_CHECKED,        // Successfully checked/removed a caught trap
        TRAP_RESET,          // Successfully reset a collapsed trap
        TRAP_RESET_INITIATED, // Reset action initiated (compound action: check + re-lay)
        TRAP_REMOVED,        // Successfully removed a trap
        MOVEMENT_REQUIRED,   // Need to move to interact with trap
        VERIFICATION_NEEDED, // Trap state unclear, needs verification
        FAILED               // Interaction failed
    }
    
    /**
     * Create a successful result.
     */
    public static InteractionResult success(InteractionType type, String message, WorldPosition position) {
        return new InteractionResult(true, type, message, position, Optional.empty());
    }
    
    /**
     * Create a failed result.
     */
    public static InteractionResult failure(String message, WorldPosition position) {
        return new InteractionResult(false, InteractionType.FAILED, message, position, Optional.empty());
    }
    
    /**
     * Create a failed result with an exception.
     */
    public static InteractionResult failure(String message, WorldPosition position, Exception error) {
        return new InteractionResult(false, InteractionType.FAILED, message, position, Optional.of(error));
    }
    
    /**
     * Create a movement required result.
     */
    public static InteractionResult movementRequired(WorldPosition position) {
        return new InteractionResult(false, InteractionType.MOVEMENT_REQUIRED, 
            "Movement required to interact with trap", position, Optional.empty());
    }
    
    /**
     * Create a verification needed result.
     */
    public static InteractionResult verificationNeeded(WorldPosition position) {
        return new InteractionResult(false, InteractionType.VERIFICATION_NEEDED,
            "Trap state uncertain, verification needed", position, Optional.empty());
    }
}