package dev.galysso.talentgraph.asset;

import dev.galysso.talentgraph.api.TalentId;
import dev.galysso.talentgraph.effect.EquipmentBaseline;
import dev.galysso.talentgraph.effect.TalentEffect;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The effects of one graph once validated: the equipment baseline and, per
 * talent, the effects that survived validation, in file order — talents
 * in file order too. Talents without effects have no entry.
 *
 * @param baseline  the graph's {@code "Equipment"} section, possibly empty
 * @param byTalent  the validated effects of each talent
 * @param ancestors the prerequisites of each talent, direct and transitive,
 *                  after the loader cut the cycles; a talent without any
 *                  has no entry
 */
public record GraphEffects(EquipmentBaseline baseline, Map<TalentId, List<TalentEffect>> byTalent,
                           Map<TalentId, Set<TalentId>> ancestors) {

    /** A graph with no effect at all. */
    public static final GraphEffects NONE = new GraphEffects(EquipmentBaseline.NONE, Map.of(), Map.of());

    public GraphEffects {
        Objects.requireNonNull(baseline, "baseline");
        // File order is kept: the ability resolver settles its last ties by it.
        byTalent = Collections.unmodifiableMap(new LinkedHashMap<>(byTalent));
        ancestors = Map.copyOf(ancestors);
    }

    /** A graph whose talents require nothing of one another. */
    public GraphEffects(EquipmentBaseline baseline, Map<TalentId, List<TalentEffect>> byTalent) {
        this(baseline, byTalent, Map.of());
    }

    /** {@return the effects of a talent, empty if it has none} */
    public List<TalentEffect> of(TalentId talent) {
        return byTalent.getOrDefault(talent, List.of());
    }

    /** {@return whether a talent requires another, directly or through others} */
    public boolean requires(TalentId talent, TalentId other) {
        return ancestors.getOrDefault(talent, Set.of()).contains(other);
    }

    public boolean isEmpty() {
        return baseline.isEmpty() && byTalent.isEmpty();
    }
}
