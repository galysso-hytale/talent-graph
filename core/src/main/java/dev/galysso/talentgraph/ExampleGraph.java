package dev.galysso.talentgraph;

import dev.galysso.talentgraph.api.TalentGraph;
import dev.galysso.talentgraph.api.TalentGraphBuilder;
import dev.galysso.talentgraph.api.TalentId;

import java.util.Set;

/**
 * A throwaway graph so {@code /talents} shows something on a fresh install.
 * Delete once real graphs are loaded from data files.
 */
final class ExampleGraph {

    private ExampleGraph() {
    }

    static TalentGraph build() {
        TalentId toughness = new TalentId("talentgraph", "toughness");
        TalentId cleave = new TalentId("talentgraph", "cleave");
        return TalentGraphBuilder.of(new TalentId("talentgraph", "example"), "Example")
                .talent(toughness, "Toughness", 3, rank -> rank)
                .talent(cleave, "Cleave", 1, rank -> 2, Set.of(toughness))
                .build();
    }
}
