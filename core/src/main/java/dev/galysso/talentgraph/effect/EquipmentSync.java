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
 *       player why (see {@link InteractionOverrides}); the refusal wins
 *       over any ability bound to those keys;</li>
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

    /** The keys a forbidden item loses; {@code Pick} is left alone, it does nothing worth refusing. */
    private static final InteractionType[] REFUSED_KEYS = {InteractionType.Primary, InteractionType.Secondary,
            InteractionType.Ability1, InteractionType.Ability2, InteractionType.Ability3};

    private EquipmentSync() {
    }

    /**
     * What the rules refuse of what the player holds. Nothing in creative
     * mode, or with no rule.
     *
     * @param held     what the player holds
     * @param rules    the rules in force for the player
     * @param creative whether the player is (about to be) in creative mode
     */
    static Refusal judge(HeldItems held, EquipmentRules rules, boolean creative) {
        if (creative || rules.isEmpty()) {
            return Refusal.NONE;
        }
        boolean handDenied = held.hand() != null && !rules.allowed(held.hand().id(), held.hand()::hasTag);
        boolean offDenied = held.off() != null && !rules.allowed(held.off().id(), held.off()::hasTag);
        return new Refusal(handDenied, offDenied, held);
    }

    /**
     * Brings the marker and the armour of a player in line with their
     * rules. The key overrides of the refusal are written by the engine,
     * merged with the abilities', through {@link InteractionOverrides}.
     *
     * @param ref        the player entity, on its world thread
     * @param accessor   the store or command buffer of that thread
     * @param refusal    what {@link #judge} found
     * @param rules      the rules in force for the player
     * @param creative   whether the player is (about to be) in creative mode
     * @param markerType the {@link HeldItemDenied} component type
     * @return how many things changed: marker, armour pieces
     */
    static int sync(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor, Refusal refusal,
                    EquipmentRules rules, boolean creative, ComponentType<EntityStore, HeldItemDenied> markerType) {
        int changes = 0;
        HeldItemDenied marker = accessor.getComponent(ref, markerType);
        if (refusal.hand || refusal.off) {
            if (marker == null) {
                accessor.addComponent(ref, markerType, new HeldItemDenied(refusal.hand, refusal.off));
                changes++;
            } else if (marker.set(refusal.hand, refusal.off)) {
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
     * Whether the item in hand and the off-hand item are forbidden.
     *
     * @param hand the item in hand is forbidden
     * @param off  the off-hand item is forbidden
     * @param held what the player holds, to tell which item each key runs
     */
    record Refusal(boolean hand, boolean off, @Nullable HeldItems held) {

        static final Refusal NONE = new Refusal(false, false, null);

        /** The keys to redirect to {@link #DENIED_ROOT}: those the forbidden item would answer. */
        Map<InteractionType, String> overrides() {
            Map<InteractionType, String> desired = new EnumMap<>(InteractionType.class);
            if (held == null) {
                return desired;
            }
            for (InteractionType key : REFUSED_KEYS) {
                if (held.offHandRuns(key) ? off : hand) {
                    desired.put(key, DENIED_ROOT);
                }
            }
            return desired;
        }
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
