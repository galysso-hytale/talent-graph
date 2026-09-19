package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.logger.sentry.SkipSentryException;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.entities.Player;
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
import dev.galysso.talentgraph.ui.AbilityHud;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
 * ranks move, a graph is loaded or removed, they respawn (vanilla clears
 * entity effects at death), their held item, off hand or armour change, or
 * their game mode does. The key overrides — equipment refusal and
 * abilities — are written in one pass, the refusal winning. Offline players are never touched;
 * their save is corrected when they come back.</p>
 */
public final class EffectEngine {

    private final HytaleLogger logger;
    private final TalentGraphApi api;
    private final EffectCatalog catalog;
    private final ComponentType<EntityStore, AppliedEffectsComponent> appliedType;
    private final ComponentType<EntityStore, HeldItemDenied> deniedType;

    public EffectEngine(HytaleLogger logger, TalentGraphApi api, EffectCatalog catalog,
                        ComponentType<EntityStore, AppliedEffectsComponent> appliedType,
                        ComponentType<EntityStore, HeldItemDenied> deniedType) {
        this.logger = logger;
        this.api = api;
        this.catalog = catalog;
        this.appliedType = appliedType;
        this.deniedType = deniedType;
    }

    /**
     * Reconciles one player entity. Must run on the entity's world thread,
     * the only place its components may be touched.
     *
     * @param ref      the player entity
     * @param accessor the store, or the command buffer of the system calling
     */
    public void sync(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        Player player = accessor.getComponent(ref, Player.getComponentType());
        sync(ref, accessor, player == null ? GameMode.Adventure : player.getGameMode());
    }

    /**
     * Reconciles one player entity as if in a given game mode: the one
     * they are about to be in, when called from the change event, which
     * fires before the mode is written.
     *
     * @param ref      the player entity
     * @param accessor the store, or the command buffer of the system calling
     * @param gameMode the mode the equipment rules are judged in
     */
    public void sync(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor, GameMode gameMode) {
        PlayerRef playerRef = accessor.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        PlayerTalents talents = api.talentsOf(playerRef.getUuid());
        int stats = 0;
        int effects = 0;
        int keys = 0;
        int equipment = 0;
        List<AbilityHud.Slot> abilities = List.of();
        EntityStatMap statMap = accessor.getComponent(ref, EntityStatMap.getComponentType());
        if (statMap != null) {
            stats = StatSync.sync(statMap, talents::rank, catalog, EntityStatType.getAssetMap()::getIndex);
        }
        // A dead player has no effects, vanilla saw to that, and no use for
        // equipment; the respawn sync does both once the corpse is a player again.
        if (accessor.getComponent(ref, DeathComponent.getComponentType()) == null) {
            AppliedEffectsComponent applied = accessor.getComponent(ref, appliedType);
            if (applied == null) {
                // First sync of a player without the record (new, or from an
                // older version): created here, at world entry, through the
                // command buffer; a store outside a system would do as well.
                applied = accessor.ensureAndGetComponent(ref, appliedType);
            }
            EffectControllerComponent controller = accessor.getComponent(ref, EffectControllerComponent.getComponentType());
            if (controller != null) {
                effects = EntityEffectSync.sync(ref, accessor, controller, applied, talents::rank, catalog);
            }
            boolean creative = gameMode == GameMode.Creative;
            EquipmentRules rules = EquipmentRules.compile(catalog, talents::rank);
            HeldItems held = HeldItems.of(ref, accessor);
            EquipmentSync.Refusal refusal = EquipmentSync.judge(held, rules, creative);
            // One write for both: the refusal takes its keys from the
            // abilities. Two partial writes would give back each other's keys.
            Map<InteractionType, AbilityRules.Bound> bound =
                    AbilitySync.bound(held, AbilityRules.compile(catalog, talents::rank));
            Map<InteractionType, String> overrides = new EnumMap<>(InteractionType.class);
            overrides.putAll(AbilitySync.desired(bound));
            overrides.putAll(refusal.overrides());
            keys = InteractionOverrides.sync(ref, accessor, applied, overrides, catalog.ownedRoots()::contains);
            equipment = EquipmentSync.sync(ref, accessor, refusal, rules, creative, deniedType);
            abilities = AbilityHud.slots(bound, References.LIVE);
        }
        // The HUD shows what was bound: nothing for a dead player, whose
        // keys were not written either.
        Player player = accessor.getComponent(ref, Player.getComponentType());
        if (player != null) {
            AbilityHud.sync(player, playerRef, abilities);
        }
        if (stats > 0 || effects > 0 || keys > 0 || equipment > 0) {
            logger.at(Level.FINE).log(
                    "Talent effects synced for %s: %d stat(s), %d entity effect(s), %d key(s), %d equipment change(s)",
                    playerRef.getUuid(), stats, effects, keys, equipment);
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
