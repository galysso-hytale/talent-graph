package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import dev.galysso.talentgraph.api.TalentId;
import dev.galysso.talentgraph.asset.GraphEffects;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;

/**
 * The equipment rules in force for one player: the {@link EquipmentEffect}s
 * of the talents they rank, and the baseline of every loaded graph.
 *
 * <p>Resolution for one item, in order:</p>
 * <ol>
 *   <li>Among the talent rules that match the item, the highest
 *       {@code Priority} wins; on a tie a rule naming the item by id beats
 *       one naming a tag; still tied, {@code Forbid} wins.</li>
 *   <li>No talent rule matches: the baseline speaks — {@code Allowed} then
 *       {@code Forbidden}, unioned across graphs.</li>
 *   <li>Nothing matches: the item is free.</li>
 * </ol>
 *
 * <p>Pure: built from the catalogue and the ranks, then queried. A sync
 * asks about six items at most, so nothing is cached.</p>
 */
public final class EquipmentRules {

    /** Rules that apply to nobody: everything is free. */
    public static final EquipmentRules NONE = new EquipmentRules(List.of(), List.of(), List.of());

    private final List<Rule> talentRules;
    private final List<ItemMatcher> baseAllowed;
    private final List<ItemMatcher> baseForbidden;

    private EquipmentRules(List<Rule> talentRules, List<ItemMatcher> baseAllowed, List<ItemMatcher> baseForbidden) {
        this.talentRules = talentRules;
        this.baseAllowed = baseAllowed;
        this.baseForbidden = baseForbidden;
    }

    /**
     * Gathers the rules of the ranked talents and the baselines of every
     * loaded graph.
     *
     * @param catalog the effects of the loaded graphs
     * @param rankOf  the rank reached in a talent, 0 if none
     */
    public static EquipmentRules compile(EffectCatalog catalog, ToIntFunction<TalentId> rankOf) {
        List<Rule> rules = new ArrayList<>();
        List<ItemMatcher> allowed = new ArrayList<>();
        List<ItemMatcher> forbidden = new ArrayList<>();
        for (GraphEffects graph : catalog.all().values()) {
            allowed.addAll(graph.baseline().allowed());
            forbidden.addAll(graph.baseline().forbidden());
            for (Map.Entry<TalentId, List<TalentEffect>> talent : graph.byTalent().entrySet()) {
                int rank = rankOf.applyAsInt(talent.getKey());
                if (rank <= 0) {
                    continue;
                }
                for (TalentEffect effect : talent.getValue()) {
                    if (effect instanceof EquipmentEffect equipment && equipment.appliesAt(rank)) {
                        for (ItemMatcher matcher : equipment.items()) {
                            rules.add(new Rule(matcher, equipment.mode(), equipment.priority()));
                        }
                    }
                }
            }
        }
        if (rules.isEmpty() && allowed.isEmpty() && forbidden.isEmpty()) {
            return NONE;
        }
        return new EquipmentRules(List.copyOf(rules), List.copyOf(allowed), List.copyOf(forbidden));
    }

    /** {@return whether nothing is ever forbidden under these rules} */
    public boolean isEmpty() {
        return this == NONE;
    }

    /** {@return whether the player may use this item} */
    public boolean allowed(Item item) {
        return allowed(item.getId(), item.getData().getExpandedTagIndexes()::contains);
    }

    /**
     * The resolution itself, on the item's id and tags.
     *
     * @param itemId the item id
     * @param hasTag whether the item carries a tag index
     */
    public boolean allowed(String itemId, IntPredicate hasTag) {
        Rule winner = null;
        for (Rule rule : talentRules) {
            if (rule.matcher.matches(itemId, hasTag) && (winner == null || rule.beats(winner))) {
                winner = rule;
            }
        }
        if (winner != null) {
            return winner.mode == EquipmentEffect.Mode.ALLOW;
        }
        for (ItemMatcher matcher : baseAllowed) {
            if (matcher.matches(itemId, hasTag)) {
                return true;
            }
        }
        for (ItemMatcher matcher : baseForbidden) {
            if (matcher.matches(itemId, hasTag)) {
                return false;
            }
        }
        return true;
    }

    private record Rule(ItemMatcher matcher, EquipmentEffect.Mode mode, int priority) {

        /** Whether this rule wins over another that also matches. */
        boolean beats(Rule other) {
            if (priority != other.priority) {
                return priority > other.priority;
            }
            if (matcher.isTag() != other.matcher.isTag()) {
                return !matcher.isTag();
            }
            return mode == EquipmentEffect.Mode.FORBID && other.mode == EquipmentEffect.Mode.ALLOW;
        }
    }
}
