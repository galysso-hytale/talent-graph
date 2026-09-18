package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.ArmorSlotAddFilter;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * The slot filter of one armour slot of one player: vanilla's own
 * {@link ArmorSlotAddFilter} (the piece must be of the slot's kind), and
 * the piece must be allowed by the player's talents.
 *
 * <p>Vanilla's filter cannot be wrapped ({@code SimpleItemContainer} keeps
 * its filters private), so this one replaces it with the same check in
 * front. Only adding is judged: taking a piece off must stay possible
 * whatever the talents say, otherwise a piece forbidden after it was put
 * on could never leave the slot. A refusal is silent in vanilla; here it
 * tells the player once per second, as the client may test the slot
 * several times for one gesture.</p>
 */
public final class TalentArmorFilter implements SlotFilter {

    private static final long NOTIFY_INTERVAL_MS = 1000;

    private final ArmorSlotAddFilter vanilla;
    private final EquipmentRules rules;
    private final Consumer<ItemStack> refused;
    private long lastNotified;

    /**
     * @param slot    the armour slot
     * @param rules   the player's rules at the last sync
     * @param refused told of a refused piece, at most once per second
     */
    public TalentArmorFilter(ItemArmorSlot slot, EquipmentRules rules, Consumer<ItemStack> refused) {
        this.vanilla = new ArmorSlotAddFilter(slot);
        this.rules = rules;
        this.refused = refused;
    }

    @Override
    public boolean test(@Nonnull FilterActionType actionType, @Nonnull ItemContainer container, short slot,
                        @Nullable ItemStack itemStack) {
        if (!vanilla.test(actionType, container, slot, itemStack)) {
            return false;
        }
        if (actionType != FilterActionType.ADD || ItemStack.isEmpty(itemStack) || rules.allowed(itemStack.getItem())) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastNotified >= NOTIFY_INTERVAL_MS) {
            lastNotified = now;
            refused.accept(itemStack);
        }
        return false;
    }
}
