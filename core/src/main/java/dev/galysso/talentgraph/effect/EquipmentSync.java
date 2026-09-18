package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainerUtil;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Reconciles what a player may use with the {@link EquipmentRules} of the
 * talents they rank. "Forbidden" means the item can still be owned,
 * carried and sold, but not used:
 * <ul>
 *   <li>the item in hand, or in the off hand, keeps its place, and its
 *       keys are redirected to {@link #DENIED_ROOT}, which tells the
 *       player why (see {@link InteractionOverrides});</li>
 *   <li>its stat modifiers are stripped ({@link HeldItemDenied},
 *       {@link DeniedItemStatsSystem});</li>
 *   <li>an armour piece is refused at the slot ({@link TalentArmorFilter}),
 *       and one already worn goes back to the bag, or to the ground when
 *       the bag is full — never destroyed.</li>
 * </ul>
 *
 * <p>Creative mode is exempt: nothing is overridden, nothing is marked,
 * and the armour filters are vanilla's alone.</p>
 */
final class EquipmentSync {

    /** The root interaction every refused key is redirected to. */
    static final String DENIED_ROOT = "TalentGraph_Denied";

    /** Keys of the main hand: refused when the item in hand is forbidden. */
    private static final InteractionType[] HAND_KEYS =
            {InteractionType.Primary, InteractionType.Ability1, InteractionType.Ability2, InteractionType.Ability3};

    private EquipmentSync() {
    }

    /**
     * Brings the overrides, the marker and the armour of a player in line
     * with their rules.
     *
     * @param ref        the player entity, on its world thread
     * @param accessor   the store or command buffer of that thread
     * @param applied    the record of what was written, updated in place
     * @param rules      the rules in force for the player
     * @param creative   whether the player is (about to be) in creative mode
     * @param markerType the {@link HeldItemDenied} component type
     * @return how many things changed: keys, marker, armour pieces
     */
    static int sync(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor, AppliedEffectsComponent applied,
                    EquipmentRules rules, boolean creative, ComponentType<EntityStore, HeldItemDenied> markerType) {
        boolean handDenied = false;
        boolean offDenied = false;
        boolean offHeld = false;
        if (!creative && !rules.isEmpty()) {
            ItemStack hand = InventoryComponent.getItemInHand(accessor, ref);
            handDenied = !ItemStack.isEmpty(hand) && !rules.allowed(hand.getItem());
            InventoryComponent.Utility utility = accessor.getComponent(ref, InventoryComponent.Utility.getComponentType());
            ItemStack off = utility == null ? null : utility.getActiveItem();
            offHeld = !ItemStack.isEmpty(off);
            offDenied = offHeld && !rules.allowed(off.getItem());
        }

        Map<InteractionType, String> desired = new EnumMap<>(InteractionType.class);
        if (handDenied) {
            for (InteractionType key : HAND_KEYS) {
                desired.put(key, DENIED_ROOT);
            }
        }
        // Right click runs the off-hand item when there is one, else the hand's.
        if (offHeld ? offDenied : handDenied) {
            desired.put(InteractionType.Secondary, DENIED_ROOT);
        }
        int changes = InteractionOverrides.sync(ref, accessor, applied, desired);

        HeldItemDenied marker = accessor.getComponent(ref, markerType);
        if (handDenied || offDenied) {
            if (marker == null) {
                accessor.addComponent(ref, markerType, new HeldItemDenied(handDenied, offDenied));
                changes++;
            } else if (marker.set(handDenied, offDenied)) {
                changes++;
            }
        } else if (marker != null) {
            accessor.tryRemoveComponent(ref, markerType);
            changes++;
        }

        changes += syncArmor(ref, accessor, rules, creative);
        return changes;
    }

    /**
     * Re-poses the four armour slot filters with a snapshot of the rules
     * (vanilla re-poses its own at every world entry), then returns the
     * worn pieces those rules refuse.
     */
    private static int syncArmor(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor,
                                 EquipmentRules rules, boolean creative) {
        InventoryComponent.Armor armor = accessor.getComponent(ref, InventoryComponent.Armor.getComponentType());
        if (armor == null || !(armor.getInventory() instanceof SimpleItemContainer container)) {
            return 0;
        }
        if (creative || rules.isEmpty()) {
            ItemContainerUtil.trySetArmorFilters(container);
            return 0;
        }
        PlayerRef playerRef = accessor.getComponent(ref, PlayerRef.getComponentType());
        ItemArmorSlot[] slots = ItemArmorSlot.VALUES;
        for (short slot = 0; slot < container.getCapacity() && slot < slots.length; slot++) {
            container.setSlotFilter(FilterActionType.ADD, slot, new TalentArmorFilter(slots[slot], rules,
                    stack -> notifyRefused(playerRef, stack)));
        }
        int returned = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack worn = container.getItemStack(slot);
            if (ItemStack.isEmpty(worn) || rules.allowed(worn.getItem())) {
                continue;
            }
            // Without the filter: ours would let it go, vanilla's judges the
            // slot's kind, but the piece is leaving whatever either says.
            if (!container.removeItemStackFromSlot(slot, false).succeeded()) {
                continue;
            }
            giveBack(ref, accessor, worn);
            notifyRefused(playerRef, worn);
            returned++;
        }
        return returned;
    }

    /** To the bag, following the player's pickup settings; the rest to the ground. */
    private static void giveBack(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor, ItemStack stack) {
        ItemStackTransaction given = Player.giveItem(stack, ref, accessor);
        ItemStack remainder = given.getRemainder();
        if (remainder != null && !remainder.isEmpty()) {
            ItemUtils.dropItem(ref, remainder, accessor);
        }
    }

    private static void notifyRefused(@Nullable PlayerRef playerRef, ItemStack stack) {
        if (playerRef != null) {
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.join(
                    Message.raw("Your talents do not let you wear "),
                    Message.translation(stack.getItem().getTranslationKey())));
        }
    }
}
