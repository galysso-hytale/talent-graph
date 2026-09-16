package dev.galysso.talentgraph.ui;

import dev.galysso.talentgraph.api.Talent;
import dev.galysso.talentgraph.api.TalentGraph;
import dev.galysso.talentgraph.api.TalentId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fallback layout for graphs that come without positions, typically ones
 * registered from code. Talents are stacked in rows by prerequisite depth:
 * roots on the first row, then everything that only needs roots, and so on.
 */
public final class AutoLayout {

    private static final int H_GAP = 160;
    private static final int V_GAP = 128;

    private AutoLayout() {
    }

    /**
     * Lays out a graph. Deterministic: the same graph always yields the same
     * layout, so a page can be rebuilt without nodes jumping around.
     *
     * @param graph the graph, already validated as acyclic
     * @return a layout covering every talent of the graph
     */
    public static GraphLayout of(TalentGraph graph) {
        Map<TalentId, Integer> depths = new HashMap<>();
        List<List<Talent>> rows = new ArrayList<>();
        for (Talent talent : graph.talents()) {
            int depth = depthOf(talent, graph, depths);
            while (rows.size() <= depth) {
                rows.add(new ArrayList<>());
            }
            rows.get(depth).add(talent);
        }
        Map<TalentId, GraphLayout.Point> positions = new HashMap<>();
        for (int row = 0; row < rows.size(); row++) {
            List<Talent> talents = rows.get(row);
            talents.sort(Comparator.comparing(t -> t.id().toString()));
            for (int column = 0; column < talents.size(); column++) {
                positions.put(talents.get(column).id(), new GraphLayout.Point(
                        GraphLayout.MARGIN + column * H_GAP,
                        GraphLayout.MARGIN + row * V_GAP));
            }
        }
        return GraphLayout.of(positions);
    }

    private static int depthOf(Talent talent, TalentGraph graph, Map<TalentId, Integer> depths) {
        Integer known = depths.get(talent.id());
        if (known != null) {
            return known;
        }
        int depth = 0;
        for (TalentId prerequisite : talent.prerequisites()) {
            // build() guarantees prerequisites exist within the graph.
            Talent required = graph.talent(prerequisite).orElseThrow();
            depth = Math.max(depth, depthOf(required, graph, depths) + 1);
        }
        depths.put(talent.id(), depth);
        return depth;
    }
}
