package dev.galysso.talentgraph.ui;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Drives the look of the ability HUD: ten times a second, reads where each
 * shown ability's cooldown stands and whether its stat costs are met, and
 * rewrites the squares that changed. A player without the HUD, or with an
 * empty one, costs a map lookup; the cooldown reads are reflective but few.
 */
public final class AbilityCooldownSystem extends DelayedEntitySystem<EntityStore> {

    private static final float INTERVAL_SECONDS = 0.1f;

    // Every player entity has an interaction manager; its component type is
    // only known once the interaction module is set up, which may be after
    // this plugin's setup, so it is fetched at the first tick.
    private final Query<EntityStore> query = Query.and(Player.getComponentType());
    private ComponentType<EntityStore, InteractionManager> managerType;

    public AbilityCooldownSystem() {
        super(INTERVAL_SECONDS);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Player player = chunk.getComponent(index, Player.getComponentType());
        assert player != null;
        if (!(player.getHudManager().getCustomHud(AbilityHud.KEY) instanceof AbilityHud hud)) {
            return;
        }
        if (managerType == null) {
            managerType = InteractionModule.get().getInteractionManagerComponent();
        }
        InteractionManager manager = chunk.getComponent(index, managerType);
        if (manager != null) {
            hud.tickUsability(manager, chunk.getComponent(index, EntityStatMap.getComponentType()));
        }
    }
}
