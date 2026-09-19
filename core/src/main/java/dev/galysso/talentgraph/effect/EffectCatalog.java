package dev.galysso.talentgraph.effect;

import dev.galysso.talentgraph.api.TalentId;
import dev.galysso.talentgraph.asset.GraphEffects;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The effects of every registered graph, keyed by graph id, kept beside
 * the registry the same way layouts are. A graph without an entry has no
 * effect, so callers never get {@code null}.
 */
public final class EffectCatalog {

    private final ConcurrentMap<TalentId, GraphEffects> effects = new ConcurrentHashMap<>();

    /**
     * Registers or replaces the effects of a graph.
     *
     * @param graphId the graph identifier
     * @param graph   its validated effects
     */
    public void put(TalentId graphId, GraphEffects graph) {
        effects.put(graphId, graph);
    }

    /**
     * Drops the effects of a graph, if any.
     *
     * @param graphId the graph identifier
     */
    public void remove(TalentId graphId) {
        effects.remove(graphId);
    }

    /** {@return the effects of a graph, {@link GraphEffects#NONE} if unknown} */
    public GraphEffects of(TalentId graphId) {
        return effects.getOrDefault(graphId, GraphEffects.NONE);
    }

    /** {@return a snapshot of every registered graph's effects} */
    public Map<TalentId, GraphEffects> all() {
        return Map.copyOf(effects);
    }

    /**
     * {@return every root interaction id an ability of a loaded graph can
     * bind, at any rank, plus the refusal's} A key holding one of these is
     * ours whatever the record says: nothing else writes them.
     */
    public Set<String> ownedRoots() {
        Set<String> ids = new HashSet<>();
        ids.add(EquipmentSync.DENIED_ROOT);
        for (GraphEffects graph : effects.values()) {
            for (List<TalentEffect> talent : graph.byTalent().values()) {
                for (TalentEffect effect : talent) {
                    if (effect instanceof AbilityEffect ability) {
                        ids.addAll(ability.interactions());
                    }
                }
            }
        }
        return ids;
    }

    /**
     * {@return every entity effect id a talent of a loaded graph can place,
     * at any rank} An infinite effect with one of these ids is ours
     * whatever the record says.
     */
    public Set<String> ownedEffects() {
        Set<String> ids = new HashSet<>();
        for (GraphEffects graph : effects.values()) {
            for (List<TalentEffect> talent : graph.byTalent().values()) {
                for (TalentEffect effect : talent) {
                    if (effect instanceof EntityEffectLink link) {
                        ids.addAll(link.ids());
                    }
                }
            }
        }
        return ids;
    }
}
