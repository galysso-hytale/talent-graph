package dev.galysso.talentgraph.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/**
 * Builds immutable {@link TalentGraph} instances.
 *
 * <p>This is the supported way to create graphs: implementing {@link Talent}
 * and {@link TalentGraph} by hand is allowed but means re-deriving the
 * immutability and validation guarantees the registry expects.</p>
 *
 * <pre>{@code
 * TalentGraph graph = TalentGraphBuilder.of(TalentId.parse("mymod:warrior"), "Warrior")
 *         .talent(TalentId.parse("mymod:toughness"), "Toughness", 3, rank -> rank)
 *         .talent(TalentId.parse("mymod:cleave"), "Cleave", 1, rank -> 2,
 *                 Set.of(TalentId.parse("mymod:toughness")))
 *         .build();
 * }</pre>
 */
public final class TalentGraphBuilder {

    private final TalentId id;
    private final String displayName;
    private final Map<TalentId, Talent> talents = new LinkedHashMap<>();

    private TalentGraphBuilder(TalentId id, String displayName) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
    }

    /**
     * Starts a new graph.
     *
     * @param id          the graph identifier
     * @param displayName the untranslated display name
     * @return a fresh builder
     */
    public static TalentGraphBuilder of(TalentId id, String displayName) {
        return new TalentGraphBuilder(id, displayName);
    }

    /**
     * Adds a talent with no prerequisites.
     *
     * @param id          the talent identifier
     * @param displayName the untranslated display name
     * @param maxRank     the highest reachable rank, at least {@code 1}
     * @param costOfRank  maps a rank to its cost in talent points
     * @return this builder
     */
    public TalentGraphBuilder talent(TalentId id, String displayName, int maxRank,
                                     IntUnaryOperator costOfRank) {
        return talent(id, displayName, maxRank, costOfRank, Set.of());
    }

    /**
     * Adds a talent.
     *
     * @param id            the talent identifier
     * @param displayName   the untranslated display name
     * @param maxRank       the highest reachable rank, at least {@code 1}
     * @param costOfRank    maps a rank to its cost in talent points
     * @param prerequisites talents required before this one unlocks
     * @return this builder
     * @throws TalentException if {@code id} was already added
     */
    public TalentGraphBuilder talent(TalentId id, String displayName, int maxRank,
                                     IntUnaryOperator costOfRank, Set<TalentId> prerequisites) {
        Objects.requireNonNull(id, "id");
        if (talents.containsKey(id)) {
            throw new TalentException("Duplicate talent in graph " + this.id + ": " + id);
        }
        talents.put(id, new ImmutableTalent(id, displayName, maxRank, costOfRank,
                Set.copyOf(prerequisites)));
        return this;
    }

    /**
     * {@return the finished, immutable graph}
     *
     * @throws TalentException if a prerequisite points outside the graph or a
     *                         cycle exists
     */
    public TalentGraph build() {
        Map<TalentId, Talent> snapshot = Map.copyOf(talents);
        for (Talent talent : snapshot.values()) {
            for (TalentId prerequisite : talent.prerequisites()) {
                if (!snapshot.containsKey(prerequisite)) {
                    throw new TalentException("Talent " + talent.id()
                            + " requires unknown talent " + prerequisite);
                }
            }
        }
        detectCycles(snapshot);
        return new ImmutableGraph(id, displayName, snapshot);
    }

    private static void detectCycles(Map<TalentId, Talent> talents) {
        Map<TalentId, Integer> state = new LinkedHashMap<>();
        for (TalentId id : talents.keySet()) {
            visit(id, talents, state, new ArrayList<>());
        }
    }

    private static void visit(TalentId id, Map<TalentId, Talent> talents,
                              Map<TalentId, Integer> state, List<TalentId> path) {
        Integer mark = state.get(id);
        if (mark != null && mark == 2) {
            return;
        }
        if (mark != null && mark == 1) {
            path.add(id);
            throw new TalentException("Cycle in talent graph: " + path);
        }
        state.put(id, 1);
        path.add(id);
        for (TalentId prerequisite : talents.get(id).prerequisites()) {
            visit(prerequisite, talents, state, path);
        }
        path.remove(path.size() - 1);
        state.put(id, 2);
    }

    private record ImmutableTalent(TalentId id, String displayName, int maxRank,
                                   IntUnaryOperator cost, Set<TalentId> prerequisites)
            implements Talent {

        private ImmutableTalent {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(cost, "cost");
            if (maxRank < 1) {
                throw new IllegalArgumentException("maxRank must be >= 1 for " + id);
            }
        }

        @Override
        public int costOfRank(int rank) {
            if (rank < 1 || rank > maxRank) {
                throw new IllegalArgumentException(
                        "Rank " + rank + " out of bounds [1, " + maxRank + "] for " + id);
            }
            int value = cost.applyAsInt(rank);
            if (value < 0) {
                throw new IllegalStateException("Negative cost for " + id + " at rank " + rank);
            }
            return value;
        }
    }

    private record ImmutableGraph(TalentId id, String displayName, Map<TalentId, Talent> byId)
            implements TalentGraph {

        @Override
        public Collection<Talent> talents() {
            return byId.values();
        }

        @Override
        public Optional<Talent> talent(TalentId id) {
            return Optional.ofNullable(byId.get(id));
        }
    }
}
