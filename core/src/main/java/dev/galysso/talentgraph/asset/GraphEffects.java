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

import javax.annotation.Nullable;

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
 * @param blurbs    the {@code "Description"} and {@code "Details"} of each
 *                  talent that wrote one, as written
 * @param icons     the resolved icon asset path of each talent that has one,
 *                  the image the ability HUD shows for the talent's abilities
 */
public record GraphEffects(EquipmentBaseline baseline, Map<TalentId, List<TalentEffect>> byTalent,
                           Map<TalentId, Set<TalentId>> ancestors, Map<TalentId, Blurb> blurbs,
                           Map<TalentId, String> icons) {

    /** A graph with no effect at all. */
    public static final GraphEffects NONE = new GraphEffects(EquipmentBaseline.NONE, Map.of(), Map.of(), Map.of(),
            Map.of());

    public GraphEffects {
        Objects.requireNonNull(baseline, "baseline");
        // File order is kept: the ability resolver settles its last ties by it.
        byTalent = Collections.unmodifiableMap(new LinkedHashMap<>(byTalent));
        ancestors = Map.copyOf(ancestors);
        blurbs = Map.copyOf(blurbs);
        icons = Map.copyOf(icons);
    }

    /** A graph whose talents require nothing of one another and say nothing of themselves. */
    public GraphEffects(EquipmentBaseline baseline, Map<TalentId, List<TalentEffect>> byTalent) {
        this(baseline, byTalent, Map.of(), Map.of(), Map.of());
    }

    /** A graph whose talents say nothing of themselves. */
    public GraphEffects(EquipmentBaseline baseline, Map<TalentId, List<TalentEffect>> byTalent,
                        Map<TalentId, Set<TalentId>> ancestors) {
        this(baseline, byTalent, ancestors, Map.of(), Map.of());
    }

    /** A graph whose talents have no icon. */
    public GraphEffects(EquipmentBaseline baseline, Map<TalentId, List<TalentEffect>> byTalent,
                        Map<TalentId, Set<TalentId>> ancestors, Map<TalentId, Blurb> blurbs) {
        this(baseline, byTalent, ancestors, blurbs, Map.of());
    }

    /**
     * What a talent says of itself, beside its effects.
     *
     * @param description one line under the title, shown everywhere; null if none
     * @param details     a longer text for the detail panel; null if none
     */
    public record Blurb(@Nullable String description, @Nullable String details) {

        /** {@return whether either text is present} */
        public boolean isEmpty() {
            return description == null && details == null;
        }
    }

    /** {@return what a talent says of itself, both parts null when it says nothing} */
    public Blurb blurb(TalentId talent) {
        return blurbs.getOrDefault(talent, new Blurb(null, null));
    }

    /** {@return the icon asset path of a talent, null if it declares none} */
    @Nullable
    public String icon(TalentId talent) {
        return icons.get(talent);
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
