package dev.galysso.talentgraph.internal;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.talentgraph.api.TalentId;
import dev.galysso.talentgraph.effect.EffectEngine;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Bridges the persisted {@link TalentProgressComponent} and the live
 * {@link PlayerTalentsImpl} when a player enters or leaves a world.
 *
 * <p>Both callbacks run on the world thread, the only place the component may
 * be read from the store. A world change is a remove followed by an add with
 * the same component instance, so the state round-trips through the snapshot.</p>
 */
public final class TalentProgressSystem extends RefSystem<EntityStore> {

    private final Set<Dependency<EntityStore>> dependencies =
            Set.of(new SystemDependency<>(Order.AFTER, PlayerSystems.PlayerSpawnedSystem.class));
    private final Query<EntityStore> query =
            Query.and(Player.getComponentType(), PlayerRef.getComponentType());

    private final HytaleLogger logger;
    private final TalentGraphApiImpl api;
    private final EffectEngine effects;
    private final ComponentType<EntityStore, TalentProgressComponent> componentType;

    public TalentProgressSystem(HytaleLogger logger, TalentGraphApiImpl api, EffectEngine effects,
                                ComponentType<EntityStore, TalentProgressComponent> componentType) {
        this.logger = logger;
        this.api = api;
        this.effects = effects;
        this.componentType = componentType;
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
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref, @Nonnull AddReason reason,
                              @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        assert playerRef != null;
        UUID uuid = playerRef.getUuid();
        TalentProgressComponent component = store.getComponent(ref, componentType);
        if (component == null) {
            component = new TalentProgressComponent();
            commandBuffer.addComponent(ref, componentType, component);
        }
        Map<TalentId, Integer> ranks = parseRanks(uuid, component.savedRanks());
        PlayerTalentsImpl talents = api.progressionOf(uuid);
        talents.load(component.savedPoints(), ranks);
        component.bind(talents);
        logger.at(Level.INFO).log("Talent progression loaded for %s: %d points, %d talent(s) ranked",
                uuid, component.savedPoints(), ranks.size());
        // Always, not only after a change: the persisted modifiers may be
        // stale if a graph was edited while the player was away.
        effects.sync(ref, commandBuffer);
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason,
                               @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        TalentProgressComponent component = store.getComponent(ref, componentType);
        if (component != null) {
            component.detach();
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null) {
            api.forget(playerRef.getUuid());
        }
    }

    private Map<TalentId, Integer> parseRanks(UUID uuid, TalentProgressComponent.RankEntry[] entries) {
        Map<TalentId, Integer> ranks = new HashMap<>();
        for (TalentProgressComponent.RankEntry entry : entries) {
            try {
                ranks.put(TalentId.parse(entry.talent()), entry.rank());
            } catch (IllegalArgumentException | NullPointerException e) {
                // A corrupt entry loses that one talent, not the whole player.
                logger.at(Level.WARNING).log("Ignoring malformed talent rank '%s' for player %s",
                        entry.talent(), uuid);
            }
        }
        return ranks;
    }
}
