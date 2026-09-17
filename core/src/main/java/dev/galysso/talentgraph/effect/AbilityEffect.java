package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;

import javax.annotation.Nullable;
import java.util.List;

/**
 * {@code "Type": "Ability"} — binds a Hytale {@code RootInteraction} to one
 * of the player's interaction slots, optionally only while a matching item
 * is held.
 *
 * <pre>{@code
 * { "Type": "Ability", "Slot": "Ability2", "Interaction": "MyPack_Root_Heal",
 *   "HeldItem": ["Family=Staff", "Family=Mace"] }
 * }</pre>
 *
 * <p>The ability is written in the admin's pack under
 * {@code Server/Item/RootInteractions/}; nothing about what it does is
 * described here. Two unlocked talents aiming at the same slot with a
 * compatible held item are settled by {@code "Priority"}, then file order.</p>
 */
public final class AbilityEffect extends TalentEffect {

    public static final String TYPE = "Ability";

    public static final BuilderCodec<AbilityEffect> CODEC = BuilderCodec.builder(
                    AbilityEffect.class, AbilityEffect::new, BASE_CODEC)
            .append(new KeyedCodec<>("Slot", Codec.STRING, false), (e, v) -> e.slotText = v, e -> e.slotText).add()
            .append(new KeyedCodec<>("Interaction", Codec.STRING, false),
                    (e, v) -> e.interaction = v, e -> e.interaction).add()
            .append(new KeyedCodec<>("HeldItem", Codec.STRING_ARRAY, false),
                    (e, v) -> e.heldItemText = v, e -> e.heldItemText).add()
            .append(new KeyedCodec<>("Priority", Codec.INTEGER, false), (e, v) -> e.priority = v, e -> e.priority).add()
            .build();

    /** The player-facing slots; the other {@code InteractionType}s are internal. */
    public static final List<InteractionType> SLOTS = List.of(InteractionType.Primary, InteractionType.Secondary,
            InteractionType.Ability1, InteractionType.Ability2, InteractionType.Ability3);

    @Nullable
    private String slotText;
    private String interaction;
    @Nullable
    private String[] heldItemText;
    private int priority;
    private InteractionType slot;
    private List<ItemMatcher> heldItem = List.of();

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    protected boolean check(EffectValidation v) {
        if (!v.present("Slot", slotText) || !v.present("Interaction", interaction)) {
            return false;
        }
        slot = parseSlot(slotText);
        if (slot == null) {
            v.warn("\"Slot\" must be one of " + SLOTS + ", got \"" + slotText + "\"; the effect is ignored");
            return false;
        }
        if (!v.refs().hasRootInteraction(interaction)) {
            v.warn("Unknown root interaction \"" + interaction + "\" (no Server/Item/RootInteractions/" + interaction
                    + ".json in any pack); the effect is ignored");
            return false;
        }
        if (heldItemText != null) {
            heldItem = List.copyOf(v.items("HeldItem", heldItemText));
            if (heldItem.isEmpty() && heldItemText.length > 0) {
                v.warn("No entry of \"HeldItem\" could be resolved; the ability would never be available, ignored");
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static InteractionType parseSlot(String text) {
        for (InteractionType slot : SLOTS) {
            if (slot.name().equalsIgnoreCase(text)) {
                return slot;
            }
        }
        return null;
    }

    public InteractionType slot() {
        return slot;
    }

    /** {@return the {@code RootInteraction} asset id} */
    public String interaction() {
        return interaction;
    }

    /** {@return the items that must be held, empty for "always"} */
    public List<ItemMatcher> heldItem() {
        return heldItem;
    }

    public int priority() {
        return priority;
    }
}
