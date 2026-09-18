package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What the engine has put on a player that the server persists on its own
 * and that carries no mark of ours: the {@code EntityEffect} ids it placed.
 *
 * <p>Stat modifiers need no such record, their keys are fixed. An entity
 * effect is stored by the server under the effect's own id, next to the
 * potions and food the player took, so without this list a talent removed
 * from a graph while the player was away would leave its effect behind
 * until the next death. The list is only ever read and written on the
 * player's world thread; the reference is volatile for the save, which
 * may serialise from another thread.</p>
 */
public final class AppliedEffectsComponent implements Component<EntityStore> {

    private static final String[] NONE = new String[0];

    public static final BuilderCodec<AppliedEffectsComponent> CODEC = BuilderCodec
            .builder(AppliedEffectsComponent.class, AppliedEffectsComponent::new)
            .append(new KeyedCodec<>("EntityEffects", Codec.STRING_ARRAY, false),
                    (c, v) -> c.entityEffects = v == null ? NONE : v,
                    c -> c.entityEffects)
            .add()
            .build();

    private volatile String[] entityEffects = NONE;

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

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        AppliedEffectsComponent copy = new AppliedEffectsComponent();
        copy.entityEffects = entityEffects.clone();
        return copy;
    }
}
