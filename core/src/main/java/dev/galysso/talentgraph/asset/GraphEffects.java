package dev.galysso.talentgraph.asset;

import dev.galysso.talentgraph.api.TalentId;
import dev.galysso.talentgraph.effect.EquipmentBaseline;
import dev.galysso.talentgraph.effect.TalentEffect;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The effects of one graph once validated: the equipment baseline and, per
 * talent, the effects that survived validation, in file order. Talents
 * without effects have no entry.
 *
 * @param baseline the graph's {@code "Equipment"} section, possibly empty
 * @param byTalent the validated effects of each talent
 */
public record GraphEffects(EquipmentBaseline baseline, Map<TalentId, List<TalentEffect>> byTalent) {

    /** A graph with no effect at all. */
    public static final GraphEffects NONE = new GraphEffects(EquipmentBaseline.NONE, Map.of());

    public GraphEffects {
        Objects.requireNonNull(baseline, "baseline");
        byTalent = Map.copyOf(byTalent);
    }

    /** {@return the effects of a talent, empty if it has none} */
    public List<TalentEffect> of(TalentId talent) {
        return byTalent.getOrDefault(talent, List.of());
    }

    public boolean isEmpty() {
        return baseline.isEmpty() && byTalent.isEmpty();
    }
}
