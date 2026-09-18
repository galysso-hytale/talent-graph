package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.RespawnSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Puts the talent effects back when a player respawns.
 *
 * <p>Death is the {@code DeathComponent} being added and respawn its
 * removal; vanilla clears every entity effect on both
 * ({@code DeathSystems.ClearEntityEffects},
 * {@code RespawnSystems.ClearEntityEffectsRespawnSystem}). The
 * {@code RespawnEvent} would be the wrong hook: it fires before the
 * respawn, so anything placed there is cleared again. This system runs on
 * the removal, after the vanilla clear.</p>
 */
public final class RespawnSyncSystem extends RefChangeSystem<EntityStore, DeathComponent> {

    private final Set<Dependency<EntityStore>> dependencies =
            Set.of(new SystemDependency<>(Order.AFTER, RespawnSystems.ClearEntityEffectsRespawnSystem.class));
    private final Query<EntityStore> query =
            Query.and(Player.getComponentType(), PlayerRef.getComponentType());

    private final EffectEngine engine;

    public RespawnSyncSystem(EffectEngine engine) {
        this.engine = engine;
    }

    @Nonnull
    @Override
    public ComponentType<EntityStore, DeathComponent> componentType() {
        return DeathComponent.getComponentType();
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
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent component,
                                 @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
    }

    @Override
    public void onComponentSet(@Nonnull Ref<EntityStore> ref, DeathComponent oldComponent,
                               @Nonnull DeathComponent newComponent, @Nonnull Store<EntityStore> store,
                               @Nonnull CommandBuffer<EntityStore> commandBuffer) {
    }

    @Override
    public void onComponentRemoved(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent component,
                                   @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        engine.sync(ref, commandBuffer);
    }
}
