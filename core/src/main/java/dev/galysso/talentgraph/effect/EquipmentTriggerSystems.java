package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.ChangeGameModeEvent;
import com.hypixel.hytale.server.core.event.events.ecs.InventoryChangeEvent;
import com.hypixel.hytale.server.core.event.events.ecs.InventorySetActiveSlotEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * The inputs of the equipment rules that vanilla changes without us:
 * the item in hand (active slot), the contents of the hand, off-hand and
 * armour sections, and the game mode. Each fires a sync.
 *
 * <p>A change event arrives after the container changed, so the sync
 * reads the new state. The armour piece the sync gives back raises a
 * second change event and a second sync, which finds nothing to do.</p>
 */
public final class EquipmentTriggerSystems {

    private static final Query<EntityStore> PLAYERS = Query.and(Player.getComponentType(), PlayerRef.getComponentType());

    private EquipmentTriggerSystems() {
    }

    /** The active slot of the hotbar, the tools or the off hand moved. */
    public static final class ActiveSlot extends EntityEventSystem<EntityStore, InventorySetActiveSlotEvent> {

        private final EffectEngine engine;

        public ActiveSlot(EffectEngine engine) {
            super(InventorySetActiveSlotEvent.class);
            this.engine = engine;
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return PLAYERS;
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull InventorySetActiveSlotEvent event) {
            engine.sync(chunk.getReferenceTo(index), commandBuffer);
        }
    }

    /** A section that can hold a used item changed; the others are ignored. */
    public static final class SectionChanged extends EntityEventSystem<EntityStore, InventoryChangeEvent> {

        private final Set<ComponentType<EntityStore, ? extends InventoryComponent>> watched = Set.of(
                InventoryComponent.Hotbar.getComponentType(), InventoryComponent.Tool.getComponentType(),
                InventoryComponent.Utility.getComponentType(), InventoryComponent.Armor.getComponentType());
        private final EffectEngine engine;

        public SectionChanged(EffectEngine engine) {
            super(InventoryChangeEvent.class);
            this.engine = engine;
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return PLAYERS;
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull InventoryChangeEvent event) {
            if (watched.contains(event.getComponentType())) {
                engine.sync(chunk.getReferenceTo(index), commandBuffer);
            }
        }
    }

    /**
     * The game mode is about to change. The event fires before the
     * change, so the target mode is passed along rather than read back.
     */
    public static final class GameModeChanged extends EntityEventSystem<EntityStore, ChangeGameModeEvent> {

        private final EffectEngine engine;

        public GameModeChanged(EffectEngine engine) {
            super(ChangeGameModeEvent.class);
            this.engine = engine;
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return PLAYERS;
        }

        @Override
        public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store,
                           @Nonnull CommandBuffer<EntityStore> commandBuffer, @Nonnull ChangeGameModeEvent event) {
            if (!event.isCancelled()) {
                engine.sync(chunk.getReferenceTo(index), commandBuffer, event.getGameMode());
            }
        }
    }
}
