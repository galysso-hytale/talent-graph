package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.PrioritySlot;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

/**
 * Which held item a key runs, the way vanilla decides it
 * ({@code InteractionContext.forInteraction}): the tools item when one is
 * in use; else the off-hand item when the hand is empty; else, both hands
 * full, the item with the higher declared priority for that key — on a
 * tie the hand's, except that right click goes to the off hand when the
 * hand's item is {@code Utility.Compatible} (one-handed), and a key the
 * hand's item does not define at all goes to the off hand.
 *
 * <p>Both the equipment refusal and the abilities judge the item vanilla
 * would run, so that a two-handed staff keeps right click whatever sits
 * in the off hand, and an empty hand with a shield lets the shield answer
 * every key.</p>
 *
 * @param hand       the item in hand (tools item if in use), null when empty
 * @param off        the off-hand item, null when empty
 * @param toolActive whether the tools item is in use, which silences the off hand
 */
public record HeldItems(@Nullable Held hand, @Nullable Held off, boolean toolActive) {

    /** What the selection needs to know of an item. */
    public interface Held {

        /** {@return the item id} */
        String id();

        /** {@return whether the item carries a tag index} */
        boolean hasTag(int tagIndex);

        /** {@return the item's declared priority for a key, in a slot} */
        int priority(InteractionType type, PrioritySlot slot);

        /** {@return whether the item leaves the off hand usable ({@code Utility.Compatible})} */
        boolean compatible();

        /** {@return whether the item defines a root interaction for a key} */
        boolean defines(InteractionType type);

        static Held of(Item item) {
            return new Held() {
                @Override
                public String id() {
                    return item.getId();
                }

                @Override
                public boolean hasTag(int tagIndex) {
                    return item.getData().getExpandedTagIndexes().contains(tagIndex);
                }

                @Override
                public int priority(InteractionType type, PrioritySlot slot) {
                    return item.getInteractionConfig().getPriorityFor(type, slot);
                }

                @Override
                public boolean compatible() {
                    return item.getUtility().isCompatible();
                }

                @Override
                public boolean defines(InteractionType type) {
                    return item.getInteractions().containsKey(type);
                }
            };
        }
    }

    /** Reads what a player holds. */
    public static HeldItems of(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        InventoryComponent.Tool tool = accessor.getComponent(ref, InventoryComponent.Tool.getComponentType());
        boolean toolActive = tool != null && tool.isUsingToolsItem();
        ItemStack hand = InventoryComponent.getItemInHand(accessor, ref);
        InventoryComponent.Utility utility = accessor.getComponent(ref, InventoryComponent.Utility.getComponentType());
        ItemStack off = utility == null ? null : utility.getActiveItem();
        return new HeldItems(ItemStack.isEmpty(hand) ? null : Held.of(hand.getItem()),
                ItemStack.isEmpty(off) ? null : Held.of(off.getItem()), toolActive);
    }

    /** {@return the item a key runs, null when neither hand answers it} */
    @Nullable
    public Held judged(InteractionType type) {
        return offHandRuns(type) ? off : hand;
    }

    /** {@return whether the off hand answers a key, as vanilla would decide} */
    public boolean offHandRuns(InteractionType type) {
        if (toolActive || off == null) {
            return false;
        }
        if (hand == null) {
            return true;
        }
        int prioHand = hand.priority(type, PrioritySlot.MainHand);
        int prioOff = off.priority(type, PrioritySlot.OffHand);
        if (prioHand == prioOff) {
            return type == InteractionType.Secondary && hand.compatible();
        }
        if (prioHand < prioOff) {
            return true;
        }
        return (type == InteractionType.Primary || type == InteractionType.Secondary) && !hand.defines(type);
    }
}
