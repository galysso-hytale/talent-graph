package dev.galysso.talentgraph.internal;

import dev.galysso.talentgraph.api.Talent;
import dev.galysso.talentgraph.api.TalentException;
import dev.galysso.talentgraph.api.TalentGraph;
import dev.galysso.talentgraph.api.TalentId;
import dev.galysso.talentgraph.api.TalentRegistry;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class TalentRegistryImpl implements TalentRegistry {

    private final ConcurrentMap<TalentId, TalentGraph> graphs = new ConcurrentHashMap<>();
    private final ConcurrentMap<TalentId, TalentGraph> owningGraph = new ConcurrentHashMap<>();

    @Override
    public void register(TalentGraph graph) {
        if (graphs.putIfAbsent(graph.id(), graph) != null) {
            throw new TalentException("A talent graph is already registered as " + graph.id());
        }
        for (Talent talent : graph.talents()) {
            TalentGraph previous = owningGraph.putIfAbsent(talent.id(), graph);
            if (previous != null) {
                // Roll back so a rejected graph leaves no partial state behind.
                graph.talents().forEach(t -> owningGraph.remove(t.id(), graph));
                graphs.remove(graph.id(), graph);
                throw new TalentException("Talent " + talent.id()
                        + " is already provided by graph " + previous.id());
            }
        }
    }

    @Override
    public Optional<TalentGraph> graph(TalentId id) {
        return Optional.ofNullable(graphs.get(id));
    }

    @Override
    public Collection<TalentGraph> graphs() {
        return Collections.unmodifiableCollection(graphs.values());
    }

    /**
     * Resolves a talent across every registered graph.
     *
     * @param id the talent identifier
     * @return the talent, or empty if no graph provides it
     */
    Optional<Talent> findTalent(TalentId id) {
        TalentGraph graph = owningGraph.get(id);
        return graph == null ? Optional.empty() : graph.talent(id);
    }

    /**
     * {@return the graph providing a talent, or empty if unknown}
     */
    Optional<TalentGraph> graphOf(TalentId talentId) {
        return Optional.ofNullable(owningGraph.get(talentId));
    }
}
