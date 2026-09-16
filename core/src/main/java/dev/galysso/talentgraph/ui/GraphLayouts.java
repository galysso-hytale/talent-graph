package dev.galysso.talentgraph.ui;

import dev.galysso.talentgraph.api.TalentGraph;
import dev.galysso.talentgraph.api.TalentId;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Layouts registered alongside graphs, keyed by graph id. Graphs without an
 * entry fall back to {@link AutoLayout}, so callers never get {@code null}.
 */
public final class GraphLayouts {

    private final ConcurrentMap<TalentId, GraphLayout> layouts = new ConcurrentHashMap<>();

    /**
     * Registers or replaces the layout of a graph.
     *
     * @param graphId the graph identifier
     * @param layout  the layout to use
     */
    public void put(TalentId graphId, GraphLayout layout) {
        layouts.put(graphId, layout);
    }

    /**
     * Drops the layout of a graph, if any.
     *
     * @param graphId the graph identifier
     */
    public void remove(TalentId graphId) {
        layouts.remove(graphId);
    }

    /**
     * Resolves the layout to draw a graph with.
     *
     * @param graph the graph to display
     * @return the registered layout, or an automatic one
     */
    public GraphLayout layoutOf(TalentGraph graph) {
        GraphLayout layout = layouts.get(graph.id());
        return layout != null ? layout : AutoLayout.of(graph);
    }
}
