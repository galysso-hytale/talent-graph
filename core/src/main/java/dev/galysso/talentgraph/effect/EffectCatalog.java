package dev.galysso.talentgraph.effect;

import dev.galysso.talentgraph.api.TalentId;
import dev.galysso.talentgraph.asset.GraphEffects;

import java.util.Map;
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
}
