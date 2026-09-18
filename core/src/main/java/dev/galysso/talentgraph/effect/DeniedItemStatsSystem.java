package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatsSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Strips the stat modifiers of a forbidden held item, every tick, from the
 * players marked {@link HeldItemDenied}.
 *
 * <p>Vanilla grants them from one place, {@code StatModifiersManager},
 * under the keys {@code *Weapon_0..n} (main hand) and {@code *Utility_0..n}
 * (off hand), and only when a recalculation was scheduled: active slot,
 * inventory change, effect placed or removed, death. There is no hook and
 * no getter of that flag, so this runs right after
 * {@code EntityStatsSystems.Recalculate} and removes those keys again. As a
 * {@code StatModifyingSystem} it is ordered before the change collection
 * and the network update: the client never sees the modifier come and go.
 * A tick where nothing was re-added costs one map lookup per stat.</p>
 *
 * <p>A counter-modifier under our own key would not do: vanilla sums the
 * multiplicative amounts of every key, so −m alone would give ×0.</p>
 */
public final class DeniedItemStatsSystem extends EntityTickingSystem<EntityStore>
        implements EntityStatsSystems.StatModifyingSystem {

    private static final String HAND_PREFIX = "*Weapon_";
    private static final String OFFHAND_PREFIX = "*Utility_";

    private final Set<Dependency<EntityStore>> dependencies =
            Set.of(new SystemDependency<>(Order.AFTER, EntityStatsSystems.Recalculate.class));
    private final ComponentType<EntityStore, HeldItemDenied> markerType;
    private final Query<EntityStore> query;

    public DeniedItemStatsSystem(ComponentType<EntityStore, HeldItemDenied> markerType) {
        this.markerType = markerType;
        this.query = Query.and(markerType, EntityStatMap.getComponentType());
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        HeldItemDenied denied = chunk.getComponent(index, markerType);
        EntityStatMap stats = chunk.getComponent(index, EntityStatMap.getComponentType());
        assert denied != null && stats != null;
        if (denied.hand()) {
            strip(stats, HAND_PREFIX);
        }
        if (denied.offhand()) {
            strip(stats, OFFHAND_PREFIX);
        }
    }

    /** Removes every {@code prefix + k} key of every stat, until a gap. */
    static void strip(EntityStatMap stats, String prefix) {
        for (int stat = 0; stat < stats.size(); stat++) {
            EntityStatValue value = stats.get(stat);
            if (value == null || value.getModifiers() == null || value.getModifiers().isEmpty()) {
                continue;
            }
            // Vanilla numbers the keys from 0 without gaps.
            for (int k = 0; stats.removeModifier(EntityStatMap.Predictable.SELF, stat, prefix + k) != null; k++) {
                // removed
            }
        }
    }
}
