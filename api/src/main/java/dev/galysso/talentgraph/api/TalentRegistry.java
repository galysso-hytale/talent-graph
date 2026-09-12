package dev.galysso.talentgraph.api;

import java.util.Collection;
import java.util.Optional;

/**
 * The place where plugins contribute their own talent graphs.
 *
 * <p>Register during your plugin's setup phase and declare
 * {@code "Galysso:talentgraph"} in your manifest {@code Dependencies} so the
 * server loads TalentGraph first.</p>
 */
public interface TalentRegistry {

    /**
     * Registers a graph.
     *
     * @param graph the graph to add
     * @throws TalentException if a graph with the same {@link TalentGraph#id()}
     *                         is already registered, or if the graph contains a
     *                         cycle or a dangling prerequisite
     */
    void register(TalentGraph graph);

    /**
     * Looks up a registered graph.
     *
     * @param id the graph identifier
     * @return the graph, or {@link Optional#empty()} if none is registered
     */
    Optional<TalentGraph> graph(TalentId id);

    /**
     * {@return every registered graph, in no particular order}
     */
    Collection<TalentGraph> graphs();
}
