package dev.galysso.talentgraph.api;

import java.util.Collection;
import java.util.Optional;

/**
 * An immutable, acyclic set of {@link Talent talents} a player progresses
 * through. A server may expose several graphs at once, for instance one per
 * class or per profession.
 */
public interface TalentGraph {

    /**
     * {@return the globally unique identifier of this graph}
     */
    TalentId id();

    /**
     * {@return the untranslated display name}
     */
    String displayName();

    /**
     * {@return every talent of this graph, in no particular order}
     */
    Collection<Talent> talents();

    /**
     * Looks up a talent by identifier.
     *
     * @param id the talent identifier
     * @return the talent, or {@link Optional#empty()} if this graph has none
     */
    Optional<Talent> talent(TalentId id);
}
