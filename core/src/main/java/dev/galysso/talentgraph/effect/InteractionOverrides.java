package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Reconciles the keys of a player's {@code Interactions} component that
 * the engine owns with the overrides its talents want.
 *
 * <p>The component redirects an interaction key ({@code Primary},
 * {@code Secondary}…) to a root interaction of ours, before the held
 * item's own ({@code InteractionContext.getRootInteractionId}); it is
 * replicated to the client and persisted with the player. Other plugins
 * may write there too, so ownership matters: a key is ours when it holds
 * an id of ours — the refusal's root or a root an ability of a loaded
 * graph can bind — <em>or</em> when the record says we wrote it. The rule
 * comes first: it is what replaying the talents means, and it heals a
 * lost record; the record only matters for an id no loaded graph knows
 * any more (a talent deleted from its file while its root still exists).
 * A key holding someone else's id is never taken nor given back. The
 * component is created when a first key is needed and removed when it
 * becomes empty, as vanilla does
 * ({@code InteractionSystems.DropUnresolvedInteractions}).</p>
 *
 * <p>Shared by the equipment refusal and the abilities: the engine merges
 * both into one wanted map and calls this once per sync, since a partial
 * call would give back the keys the other partial call wants.</p>
 */
final class InteractionOverrides {

    private InteractionOverrides() {
    }

    /**
     * What one sync should do to the keys.
     *
     * @param remove the keys to give back
     * @param write  the root interaction id to write under each key
     * @param owned  the record after the sync: every key that then holds an id of ours
     */
    record Plan(Set<InteractionType> remove, Map<InteractionType, String> write, Map<InteractionType, String> owned) {

        int changes() {
            return remove.size() + write.size();
        }
    }

    /**
     * Decides the writes, without touching anything.
     *
     * @param recorded what the record says we wrote
     * @param current  what the component holds, key by key
     * @param desired  the root interaction id wanted under each key
     * @param ours     whether an id is one of ours
     */
    static Plan plan(Map<InteractionType, String> recorded, Map<InteractionType, String> current,
                     Map<InteractionType, String> desired, Predicate<String> ours) {
        Map<InteractionType, String> owned = new EnumMap<>(InteractionType.class);
        owned.putAll(recorded);
        // The rule first: a key holding an id of ours is ours, recorded or not.
        for (Map.Entry<InteractionType, String> e : current.entrySet()) {
            if (ours.test(e.getValue())) {
                owned.put(e.getKey(), e.getValue());
            }
        }
        Set<InteractionType> remove = EnumSet.noneOf(InteractionType.class);
        for (var it = owned.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<InteractionType, String> e = it.next();
            if (e.getValue().equals(desired.get(e.getKey()))) {
                continue;
            }
            if (e.getValue().equals(current.get(e.getKey()))) {
                remove.add(e.getKey());
            }
            // Else a key of ours rewritten by someone else: forgotten, not touched.
            it.remove();
        }
        Map<InteractionType, String> write = new EnumMap<>(InteractionType.class);
        for (Map.Entry<InteractionType, String> e : desired.entrySet()) {
            InteractionType type = e.getKey();
            if (remove.remove(type)) {
                // A key of ours changing root (a rank up): rewritten, not given back.
                write.put(type, e.getValue());
                owned.put(type, e.getValue());
                continue;
            }
            String now = current.get(type);
            if (now != null && !owned.containsKey(type)) {
                // Someone else's override: theirs to keep.
                continue;
            }
            if (!e.getValue().equals(now)) {
                write.put(type, e.getValue());
            }
            owned.put(type, e.getValue());
        }
        return new Plan(remove, write, owned);
    }

    /**
     * Brings the keys we own in line with the wanted overrides.
     *
     * @param ref      the player entity, on its world thread
     * @param accessor the store or command buffer of that thread
     * @param applied  the record of what was written, updated in place
     * @param desired  the root interaction id wanted under each key
     * @param ours     whether a root interaction id is one of ours
     * @return how many keys were written or given back
     */
    static int sync(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor,
                    AppliedEffectsComponent applied, Map<InteractionType, String> desired, Predicate<String> ours) {
        Interactions interactions = accessor.getComponent(ref, Interactions.getComponentType());
        Map<InteractionType, String> current = new EnumMap<>(InteractionType.class);
        if (interactions != null) {
            current.putAll(interactions.getInteractions());
        }
        Plan plan = plan(applied.interactions(), current, desired, ours);
        for (InteractionType type : plan.remove()) {
            interactions.removeInteractionId(type);
        }
        if (!plan.write().isEmpty() && interactions == null) {
            interactions = accessor.ensureAndGetComponent(ref, Interactions.getComponentType());
        }
        for (Map.Entry<InteractionType, String> e : plan.write().entrySet()) {
            interactions.setInteractionId(e.getKey(), e.getValue());
        }
        if (interactions != null && interactions.isEmpty()) {
            accessor.tryRemoveComponent(ref, Interactions.getComponentType());
        }
        applied.setInteractions(plan.owned());
        return plan.changes();
    }
}
