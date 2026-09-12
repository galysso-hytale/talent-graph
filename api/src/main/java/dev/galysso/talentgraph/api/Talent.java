package dev.galysso.talentgraph.api;

import java.util.Set;

/**
 * A single node of a {@link TalentGraph}.
 *
 * <p>Implementations must be immutable and safe to share between threads: the
 * Hytale server runs plugin logic on virtual threads and a talent definition
 * may be read concurrently by several players.</p>
 */
public interface Talent {

    /**
     * {@return the globally unique identifier of this talent}
     */
    TalentId id();

    /**
     * {@return the untranslated display name}
     */
    String displayName();

    /**
     * {@return the highest rank a player can reach, at least {@code 1}}
     */
    int maxRank();

    /**
     * Returns the number of talent points required to go from {@code rank - 1}
     * to {@code rank}.
     *
     * @param rank the target rank, between {@code 1} and {@link #maxRank()}
     * @return the cost in talent points, never negative
     * @throws IllegalArgumentException if {@code rank} is out of bounds
     */
    int costOfRank(int rank);

    /**
     * Returns the talents that must be unlocked at rank 1 or above before this
     * one becomes available.
     *
     * @return an immutable set of prerequisites, possibly empty
     */
    Set<TalentId> prerequisites();
}
