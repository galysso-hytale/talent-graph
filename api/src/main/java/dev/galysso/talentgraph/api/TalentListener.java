package dev.galysso.talentgraph.api;

import dev.galysso.talentgraph.api.event.TalentUnlockedEvent;

/**
 * Callback surface for plugins reacting to progression.
 *
 * <p>Every method is a no-op by default so new events can be added without
 * breaking existing implementations.</p>
 */
public interface TalentListener {

    /**
     * Called after a rank was unlocked and the points were spent.
     *
     * @param event the event describing what changed
     */
    default void onTalentUnlocked(TalentUnlockedEvent event) {
        // no-op
    }
}
