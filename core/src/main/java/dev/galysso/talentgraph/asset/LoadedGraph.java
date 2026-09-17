package dev.galysso.talentgraph.asset;

import dev.galysso.talentgraph.api.TalentGraph;
import dev.galysso.talentgraph.ui.GraphLayout;

import java.util.Objects;

/**
 * What a graph file turned into: a graph that can always be drawn, where
 * to draw it, and what was wrong with the file. When the report holds an
 * error the graph is a repaired preview for the author, never registered.
 *
 * @param graph   the graph, valid by construction
 * @param layout  its layout, including the ghosts of the report
 * @param effects what its talents do, validated
 * @param report  the problems found, possibly none
 */
public record LoadedGraph(TalentGraph graph, GraphLayout layout, GraphEffects effects, GraphReport report) {

    public LoadedGraph {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(report, "report");
    }

    /** {@return a registered graph as loaded, with nothing to report} */
    public static LoadedGraph of(TalentGraph graph, GraphLayout layout, GraphEffects effects) {
        return new LoadedGraph(graph, layout, effects, GraphReport.clean(graph.id().path() + ".json"));
    }

    /** {@return the same graph, layout and effects under another report} */
    public LoadedGraph withReport(GraphReport report) {
        return new LoadedGraph(graph, layout, effects, report);
    }
}
