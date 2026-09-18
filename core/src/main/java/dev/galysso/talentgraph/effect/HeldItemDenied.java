package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Marks a player whose held item, off-hand item or both are forbidden by
 * their talents, so that {@link DeniedItemStatsSystem} strips the stat
 * modifiers those items would grant. Placed and removed by the equipment
 * sync; never persisted, the sync at world entry puts it back.
 */
public final class HeldItemDenied implements Component<EntityStore> {

    private boolean hand;
    private boolean offhand;

    public HeldItemDenied() {
    }

    public HeldItemDenied(boolean hand, boolean offhand) {
        this.hand = hand;
        this.offhand = offhand;
    }

    /** {@return whether the item in the main hand is forbidden} */
    public boolean hand() {
        return hand;
    }

    /** {@return whether the item in the off hand is forbidden} */
    public boolean offhand() {
        return offhand;
    }

    /**
     * Updates both flags in place.
     *
     * @return whether either changed
     */
    public boolean set(boolean hand, boolean offhand) {
        if (this.hand == hand && this.offhand == offhand) {
            return false;
        }
        this.hand = hand;
        this.offhand = offhand;
        return true;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        return new HeldItemDenied(hand, offhand);
    }
}
