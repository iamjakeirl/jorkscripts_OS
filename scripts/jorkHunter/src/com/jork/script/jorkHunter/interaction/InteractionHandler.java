package com.jork.script.jorkHunter.interaction;

import com.osmb.api.location.position.types.WorldPosition;
import com.jork.script.jorkHunter.state.TrapState;
import com.jork.script.jorkHunter.state.TrapInfo;

/**
 * Interface for handling trap interactions.
 * Implementations will handle the actual UI interaction logic.
 */
public interface InteractionHandler {
    /**
     * Attempt to interact with a trap at the specified position.
     * 
     * @param trapInfo The trap information including position and current state
     * @return Result of the interaction attempt
     */
    InteractionResult interact(TrapInfo trapInfo);
    
    /**
     * Check if interaction is possible with a trap at the given position.
     * This might check visibility, distance, UI occlusion, etc.
     * 
     * @param position The position to check
     * @return true if interaction is possible
     */
    boolean canInteract(WorldPosition position);
    
    /**
     * Perform a blind tap verification at the specified position.
     * Used when trap state is uncertain.
     * 
     * @param position The position to verify
     * @return Result of the verification attempt
     */
    InteractionResult verifyTrapState(WorldPosition position);
}