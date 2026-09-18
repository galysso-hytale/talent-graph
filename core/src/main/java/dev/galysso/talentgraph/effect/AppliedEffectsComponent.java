package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What the engine has put on a player that the server persists on its own
 * and that carries no mark of ours: the {@code EntityEffect} ids it placed,
 * and the {@code Interactions} keys it overrode.
 *
 * <p>Stat modifiers need no such record, their keys are fixed. An entity
 * effect is stored by the server under the effect's own id, next to the
 * potions and food the player took, so without this list a talent removed
 * from a graph while the player was away would leave its effect behind
 * until the next death. An interaction override sits in the player's
 * {@code Interactions} component beside those of other plugins, so the
 * record tells which keys are ours and what we wrote there. The arrays are
 * only ever read and written on the player's world thread; the references
 * are volatile for the save, which may serialise from another thread.</p>
 */
public final class AppliedEffectsComponent implements Component<EntityStore> {

    private static final String[] NONE = new String[0];

    public static final BuilderCodec<AppliedEffectsComponent> CODEC = BuilderCodec
            .builder(AppliedEffectsComponent.class, AppliedEffectsComponent::new)
            .append(new KeyedCodec<>("EntityEffects", Codec.STRING_ARRAY, false),
                    (c, v) -> c.entityEffects = v == null ? NONE : v,
                    c -> c.entityEffects)
            .add()
            // "Type=RootInteractionId" per overridden key.
            .append(new KeyedCodec<>("Interactions", Codec.STRING_ARRAY, false),
                    (c, v) -> c.interactions = v == null ? NONE : v,
                    c -> c.interactions)
            .add()
            .build();

    private volatile String[] entityEffects = NONE;
    private volatile String[] interactions = NONE;

    /** {@return the effect ids placed by a talent, in placement order} */
    public Set<String> entityEffects() {
        return new LinkedHashSet<>(Arrays.asList(entityEffects));
    }

    /**
     * Records the effect ids now placed.
     *
     * @return whether the record changed
     */
    public boolean setEntityEffects(Set<String> ids) {
        String[] next = ids.toArray(NONE);
        if (Arrays.equals(next, entityEffects)) {
            return false;
        }
        entityEffects = next;
        return true;
    }

    /**
     * {@return the interaction keys overridden by a talent and the root
     * interaction id written under each} A malformed entry is dropped.
     */
    public Map<InteractionType, String> interactions() {
        Map<InteractionType, String> owned = new EnumMap<>(InteractionType.class);
        for (String entry : interactions) {
            int eq = entry.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            try {
                owned.put(InteractionType.valueOf(entry.substring(0, eq)), entry.substring(eq + 1));
            } catch (IllegalArgumentException e) {
                // An interaction type this server no longer has: forgotten.
            }
        }
        return owned;
    }

    /**
     * Records the interaction keys now overridden.
     *
     * @return whether the record changed
     */
    public boolean setInteractions(Map<InteractionType, String> owned) {
        String[] next = new String[owned.size()];
        int i = 0;
        for (Map.Entry<InteractionType, String> e : owned.entrySet()) {
            next[i++] = e.getKey().name() + "=" + e.getValue();
        }
        if (Arrays.equals(next, interactions)) {
            return false;
        }
        interactions = next;
        return true;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        AppliedEffectsComponent copy = new AppliedEffectsComponent();
        copy.entityEffects = entityEffects.clone();
        copy.interactions = interactions.clone();
        return copy;
    }
}
