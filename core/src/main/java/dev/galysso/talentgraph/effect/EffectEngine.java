package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.logger.sentry.SkipSentryException;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.talentgraph.api.PlayerTalents;
import dev.galysso.talentgraph.api.TalentGraphApi;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Applies the effects of the talents a player ranks, as one idempotent
 * reconciliation: the desired state is computed from the ranks and the
 * loaded graphs, compared with what the player carries, and only the
 * difference is written.
 *
 * <p>Nothing is replayed when a stat is read or on a tick. A sync runs
 * only when one of its inputs changes: the player enters a world, their
 * ranks move, a graph is loaded or removed, or they respawn (vanilla
 * clears entity effects at death). Offline players are never touched;
 * their save is corrected when they come back.</p>
 */
public final class EffectEngine {

    private final HytaleLogger logger;
    private final TalentGraphApi api;
    private final EffectCatalog catalog;
    private final ComponentType<EntityStore, AppliedEffectsComponent> appliedType;

    public EffectEngine(HytaleLogger logger, TalentGraphApi api, EffectCatalog catalog,
                        ComponentType<EntityStore, AppliedEffectsComponent> appliedType) {
        this.logger = logger;
        this.api = api;
        this.catalog = catalog;
        this.appliedType = appliedType;
    }

    /**
     * Reconciles one player entity. Must run on the entity's world thread,
     * the only place its components may be touched.
     *
     * @param ref      the player entity
     * @param accessor the store, or the command buffer of the system calling
     */
    public void sync(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        PlayerRef playerRef = accessor.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        PlayerTalents talents = api.talentsOf(playerRef.getUuid());
        int stats = 0;
        int effects = 0;
        EntityStatMap statMap = accessor.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap != null) {
            stats = StatSync.sync(statMap, talents::rank, catalog, EntityStatType.getAssetMap()::getIndex);
        }
        EffectControllerComponent controller = accessor.getComponent(ref, EffectControllerComponent.getComponentType());
        // A dead player has no effects, vanilla saw to that; the respawn
        // sync puts ours back once the corpse is a player again.
        if (controller != null && accessor.getComponent(ref, DeathComponent.getComponentType()) == null) {
            AppliedEffectsComponent applied = accessor.getComponent(ref, appliedType);
            if (applied == null) {
                // First sync of a player without the record (new, or from an
                // older version): created here, at world entry, through the
                // command buffer; a store outside a system would do as well.
                applied = accessor.ensureAndGetComponent(ref, appliedType);
            }
            effects = EntityEffectSync.sync(ref, accessor, controller, applied, talents::rank, catalog);
        }
        if (stats > 0 || effects > 0) {
            logger.at(Level.FINE).log("Talent effects synced for %s: %d stat(s), %d entity effect(s) changed",
                    playerRef.getUuid(), stats, effects);
        }
    }

    /**
     * Reconciles a player if they are online, from any thread. A player
     * who is offline, or between two worlds, is left alone: the next world
     * entry syncs them.
     *
     * @param playerId the player identifier
     */
    public void sync(UUID playerId) {
        PlayerRef playerRef = Universe.get().getPlayer(playerId);
        if (playerRef != null) {
            sync(playerRef);
        }
    }

    /** Reconciles every online player, from any thread. */
    public void syncAll() {
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            sync(playerRef);
        }
    }

    private void sync(PlayerRef playerRef) {
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        World world = store.getExternalData().getWorld();
        if (world.isInThread()) {
            sync(ref, store);
            return;
        }
        // A world that is shutting down refuses tasks; its players are on
        // their way out and will be synced wherever they land.
        try {
            world.execute(() -> {
                if (ref.isValid()) {
                    sync(ref, store);
                }
            });
        } catch (SkipSentryException e) {
            logger.at(Level.FINE).log("Skipping talent effect sync for %s: %s", playerRef.getUuid(), e.getMessage());
        }
    }
}
