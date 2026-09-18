package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;

/**
 * Reconciles the keys of a player's {@code Interactions} component that
 * the engine owns with the overrides its talents want.
 *
 * <p>The component redirects an interaction key ({@code Primary},
 * {@code Secondary}…) to a root interaction of ours, before the held
 * item's own ({@code InteractionContext.getRootInteractionId}); it is
 * replicated to the client and persisted with the player. Other plugins
 * may write there too, so a key is only taken when it is free or already
 * ours, and only given back when it still holds what we wrote; a key of
 * ours rewritten by someone else is forgotten, not touched. The component
 * is created when a first key is needed and removed when it becomes empty,
 * as vanilla does ({@code InteractionSystems.DropUnresolvedInteractions}).</p>
 *
 * <p>Shared by the equipment refusal and the abilities: the engine merges
 * both into one wanted map and calls this once per sync, since a partial
 * call would give back the keys the other partial call wants.</p>
 */
final class InteractionOverrides {

    private InteractionOverrides() {
    }

    /**
     * Brings the keys we own in line with the wanted overrides.
     *
     * @param ref      the player entity, on its world thread
     * @param accessor the store or command buffer of that thread
     * @param applied  the record of what was written, updated in place
     * @param desired  the root interaction id wanted under each key
     * @return how many keys were written or given back
     */
    static int sync(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor,
                    AppliedEffectsComponent applied, Map<InteractionType, String> desired) {
        Map<InteractionType, String> owned = applied.interactions();
        Interactions interactions = accessor.getComponent(ref, Interactions.getComponentType());
        int changes = 0;
        for (var it = owned.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<InteractionType, String> e = it.next();
            if (e.getValue().equals(desired.get(e.getKey()))) {
                continue;
            }
            if (interactions != null && e.getValue().equals(interactions.getInteractionId(e.getKey()))) {
                interactions.removeInteractionId(e.getKey());
                changes++;
            }
            it.remove();
        }
        for (Map.Entry<InteractionType, String> e : desired.entrySet()) {
            InteractionType type = e.getKey();
            String current = interactions == null ? null : interactions.getInteractionId(type);
            if (current != null && !owned.containsKey(type)) {
                // Someone else's override: theirs to keep.
                continue;
            }
            if (!e.getValue().equals(current)) {
                if (interactions == null) {
                    interactions = accessor.ensureAndGetComponent(ref, Interactions.getComponentType());
                }
                interactions.setInteractionId(type, e.getValue());
                changes++;
            }
            owned.put(type, e.getValue());
        }
        if (interactions != null && interactions.isEmpty()) {
            accessor.tryRemoveComponent(ref, Interactions.getComponentType());
        }
        applied.setInteractions(owned);
        return changes;
    }
}
