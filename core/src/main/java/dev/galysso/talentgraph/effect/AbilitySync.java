package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.protocol.InteractionType;

import java.util.Map;

/**
 * Asks the {@link AbilityRules} which root interaction each slot should
 * run for what a player holds. The writing itself goes through
 * {@link InteractionOverrides}, once, together with the equipment refusal
 * — which wins on every key it claims: a forbidden item in hand keeps its
 * refusal whatever ability is bound to the key.
 *
 * <p>Abilities are bound in creative mode as well; only the equipment
 * rules are exempt there.</p>
 */
final class AbilitySync {

    private AbilitySync() {
    }

    /**
     * The overrides the abilities want for a player.
     *
     * @param held  what the player holds
     * @param rules the abilities in force for the player
     * @return the root interaction id wanted under each slot
     */
    static Map<InteractionType, String> desired(HeldItems held, AbilityRules rules) {
        return rules.isEmpty() ? Map.of() : rules.resolve(held);
    }
}
