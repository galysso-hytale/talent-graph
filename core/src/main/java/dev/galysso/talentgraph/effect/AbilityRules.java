package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.protocol.InteractionType;
import dev.galysso.talentgraph.api.TalentId;
import dev.galysso.talentgraph.asset.GraphEffects;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;

/**
 * The abilities in force for one player: the {@link AbilityEffect}s of the
 * talents they rank, at their current rank, and which root interaction
 * each slot should run given what is held.
 *
 * <p>For one slot, a candidate applies when it has no {@code HeldItem}, or
 * when the judged item — the one vanilla would run for that key, see
 * {@link HeldItems} — matches one of its entries; no item matches nothing.
 * Between applicable candidates, in order:</p>
 * <ol>
 *   <li>the highest {@code Priority};</li>
 *   <li>the talent that requires the other, directly or transitively — a
 *       deeper talent is the stronger version;</li>
 *   <li>the more specific match: an item id over a tag over no
 *       {@code HeldItem};</li>
 *   <li>file order, then graph id.</li>
 * </ol>
 *
 * <p>Pure: built from the catalogue and the ranks, then queried; the sync
 * asks about six slots, so nothing is cached.</p>
 */
public final class AbilityRules {

    /** Rules that bind nothing. */
    public static final AbilityRules NONE = new AbilityRules(Map.of());

    private final Map<InteractionType, List<Candidate>> bySlot;

    private AbilityRules(Map<InteractionType, List<Candidate>> bySlot) {
        this.bySlot = bySlot;
    }

    /**
     * Gathers the abilities of the ranked talents.
     *
     * @param catalog the effects of the loaded graphs
     * @param rankOf  the rank reached in a talent, 0 if none
     */
    public static AbilityRules compile(EffectCatalog catalog, ToIntFunction<TalentId> rankOf) {
        Map<InteractionType, List<Candidate>> bySlot = new EnumMap<>(InteractionType.class);
        int order = 0;
        // Graphs in a fixed order, so a tie across graphs is settled the same way every time.
        List<Map.Entry<TalentId, GraphEffects>> graphs = new ArrayList<>(catalog.all().entrySet());
        graphs.sort(Comparator.comparing(e -> e.getKey().toString()));
        for (Map.Entry<TalentId, GraphEffects> g : graphs) {
            GraphEffects graph = g.getValue();
            for (Map.Entry<TalentId, List<TalentEffect>> talent : graph.byTalent().entrySet()) {
                int rank = rankOf.applyAsInt(talent.getKey());
                if (rank <= 0) {
                    continue;
                }
                for (TalentEffect effect : talent.getValue()) {
                    if (effect instanceof AbilityEffect ability && ability.appliesAt(rank)) {
                        bySlot.computeIfAbsent(ability.slot(), k -> new ArrayList<>()).add(new Candidate(
                                ability.interactionAt(rank), ability.heldItem(), ability.priority(),
                                talent.getKey(), graph, order++));
                    }
                }
            }
        }
        return bySlot.isEmpty() ? NONE : new AbilityRules(bySlot);
    }

    /** {@return whether no ability is bound under these rules} */
    public boolean isEmpty() {
        return bySlot.isEmpty();
    }

    /**
     * The root interaction each slot should run, judged on the item
     * vanilla would run for that key ({@link HeldItems#judged}).
     *
     * @param held what the player holds
     * @return the root interaction id per slot, only the slots that have one
     */
    public Map<InteractionType, String> resolve(HeldItems held) {
        Map<InteractionType, String> desired = new EnumMap<>(InteractionType.class);
        for (InteractionType slot : bySlot.keySet()) {
            HeldItems.Held judged = held.judged(slot);
            String root = judged == null
                    ? resolve(slot, null, tag -> false)
                    : resolve(slot, judged.id(), judged::hasTag);
            if (root != null) {
                desired.put(slot, root);
            }
        }
        return desired;
    }

    /**
     * The resolution itself, on the judged item's id and tags.
     *
     * @param slot   the slot
     * @param itemId the judged item's id, null for an empty hand
     * @param hasTag whether the judged item carries a tag index
     * @return the root interaction id, or null when nothing applies
     */
    @Nullable
    public String resolve(InteractionType slot, @Nullable String itemId, IntPredicate hasTag) {
        Candidate winner = null;
        int winnerSpecificity = -1;
        for (Candidate candidate : bySlot.getOrDefault(slot, List.of())) {
            int specificity = candidate.specificity(itemId, hasTag);
            if (specificity < 0) {
                continue;
            }
            if (winner == null || candidate.beats(specificity, winner, winnerSpecificity)) {
                winner = candidate;
                winnerSpecificity = specificity;
            }
        }
        return winner == null ? null : winner.rootId;
    }

    private record Candidate(String rootId, List<ItemMatcher> heldItem, int priority, TalentId talent,
                             GraphEffects graph, int order) {

        /** How well the judged item fits: 2 by id, 1 by tag, 0 unconditional, -1 not at all. */
        int specificity(@Nullable String itemId, IntPredicate hasTag) {
            if (heldItem.isEmpty()) {
                return 0;
            }
            if (itemId == null) {
                return -1;
            }
            int best = -1;
            for (ItemMatcher matcher : heldItem) {
                if (matcher.matches(itemId, hasTag)) {
                    best = Math.max(best, matcher.isTag() ? 1 : 2);
                }
            }
            return best;
        }

        /** Whether this candidate wins over another that also applies. */
        boolean beats(int specificity, Candidate other, int otherSpecificity) {
            if (priority != other.priority) {
                return priority > other.priority;
            }
            if (graph == other.graph) {
                if (graph.requires(talent, other.talent)) {
                    return true;
                }
                if (graph.requires(other.talent, talent)) {
                    return false;
                }
            }
            if (specificity != otherSpecificity) {
                return specificity > otherSpecificity;
            }
            return order < other.order;
        }
    }
}
