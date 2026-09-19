package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code "Type": "Ability"} — binds a Hytale {@code RootInteraction} to one
 * of the player's interaction slots, optionally only while a matching item
 * is held.
 *
 * <pre>{@code
 * { "Type": "Ability", "Slot": "Ability2", "Interaction": "MyPack_Root_Heal",
 *   "HeldItem": ["Family=Staff", "Family=Mace"] }
 * { "Type": "Ability", "Slot": "Primary", "Interaction": ["MyPack_Bolt_1", "MyPack_Bolt_2"],
 *   "HeldItem": ["Family=Staff"] }
 * }</pre>
 *
 * <p>The ability is written in the admin's pack under
 * {@code Server/Item/RootInteractions/}; nothing about what it does is
 * described here. {@code Interaction} takes one id or one per rank, the
 * last repeating, and only the id of the current rank is bound.</p>
 *
 * <p>Several unlocked talents aiming at the same slot with an applicable
 * item are settled by {@link AbilityRules}: {@code "Priority"}, then the
 * talent that requires the other, then the more specific {@code HeldItem}
 * (an id over a tag over none), then file order. An item the player's
 * equipment rules forbid keeps its refusal whatever the abilities say.</p>
 */
public final class AbilityEffect extends TalentEffect {

    public static final String TYPE = "Ability";

    public static final BuilderCodec<AbilityEffect> CODEC = BuilderCodec.builder(
                    AbilityEffect.class, AbilityEffect::new, BASE_CODEC)
            .append(new KeyedCodec<>("Slot", Codec.STRING, false), (e, v) -> e.slotText = v, e -> e.slotText).add()
            .append(new KeyedCodec<>("Interaction", Ids.CODEC, false),
                    (e, v) -> e.interactions = v, e -> e.interactions).add()
            .append(new KeyedCodec<>("HeldItem", Codec.STRING_ARRAY, false),
                    (e, v) -> e.heldItemText = v, e -> e.heldItemText).add()
            .append(new KeyedCodec<>("Priority", Codec.INTEGER, false), (e, v) -> e.priority = v, e -> e.priority).add()
            // Words for the notes of the line, when the chain hides them from
            // the describer: a cost behind a Condition, a cooldown inside the
            // chain. Free text or a key of the translation tables.
            .append(new KeyedCodec<>("Cost", Codec.STRING, false), (e, v) -> e.costText = v, e -> e.costText).add()
            .append(new KeyedCodec<>("Cooldown", Codec.STRING, false),
                    (e, v) -> e.cooldownText = v, e -> e.cooldownText).add()
            .build();

    /**
     * The slots a key of the client starts: left click, right click, and
     * the three ability keys and pick key (Q, E, R and middle click by
     * default). {@code Use} is left out on purpose: it opens doors and
     * talks to NPCs even with a weapon in hand, as every item completes its
     * own map from the unarmed one.
     */
    public static final List<InteractionType> SLOTS = List.of(InteractionType.Primary, InteractionType.Secondary,
            InteractionType.Ability1, InteractionType.Ability2, InteractionType.Ability3, InteractionType.Pick);

    @Nullable
    private String slotText;
    private String[] interactions;
    @Nullable
    private String[] heldItemText;
    private int priority;
    @Nullable
    private String costText;
    @Nullable
    private String cooldownText;
    private InteractionType slot;
    private List<ItemMatcher> heldItem = List.of();

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    protected boolean check(EffectValidation v) {
        if (!v.present("Slot", slotText)) {
            return false;
        }
        slot = parseSlot(slotText);
        if (slot == null) {
            v.warn("\"Slot\" must be one of " + SLOTS + ", got \"" + slotText + "\"; the effect is ignored");
            return false;
        }
        if (!Ids.check(v, "Interaction", "root interaction", "Server/Item/RootInteractions/", interactions,
                v.refs()::hasRootInteraction)) {
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

    /**
     * {@code "[Q] Nova"} with the notes {@code Cost — 100% signature energy},
     * {@code Cooldown — 12 s}, {@code With — a staff or a wand}: the default
     * key of the slot, the name of the rank's root interaction
     * ({@code "Description"}, else a translation under
     * {@code talentgraph.ability.<Id>}, else the id as words), then the
     * conditions in a fixed order — the cost a {@code StatsCondition} at
     * the top of the chain states (or the {@code "Cost"} written on the
     * effect), the root's cooldown (or the {@code "Cooldown"} written), the
     * item to hold. Other conditions live inside the chain and are not
     * read. Always a gain.
     */
    @Override
    protected Line describe(EffectDescriber d, int rank) {
        String id = interactionAt(rank);
        String name = d.label(description, "ability." + id, EffectDescriber.humanise(id));
        List<Line.Note> notes = new ArrayList<>();
        String cost = d.texts().keyOrRaw(costText);
        if (cost == null) {
            cost = d.cost(id);
        }
        if (cost != null) {
            notes.add(d.note("cost", cost));
        }
        String cooldown = d.texts().keyOrRaw(cooldownText);
        if (cooldown == null && d.refs().rootCooldown(id) > 0) {
            cooldown = d.seconds(d.refs().rootCooldown(id));
        }
        if (cooldown != null) {
            notes.add(d.note("cooldown", cooldown));
        }
        if (!heldItem.isEmpty()) {
            notes.add(d.note("heldItem", d.items(heldItem, true, "list.or")));
        }
        return new Line(Line.Category.ABILITIES, Line.Sign.GAIN, d.get("ability.key", "key", d.key(slot)), name, "",
                notes, SLOTS.indexOf(slot), slot, fromRank, true);
    }

    public InteractionType slot() {
        return slot;
    }

    /** {@return the {@code RootInteraction} asset id bound at a rank, the last one repeating; rank counts from 1} */
    public String interactionAt(int rank) {
        return Ids.at(interactions, rank);
    }

    /** {@return every root interaction the effect can bind, in rank order, without repeats} */
    public Set<String> interactions() {
        return new LinkedHashSet<>(Arrays.asList(interactions));
    }

    /** {@return the items that must be held, empty for "always"} */
    public List<ItemMatcher> heldItem() {
        return heldItem;
    }

    public int priority() {
        return priority;
    }
}
