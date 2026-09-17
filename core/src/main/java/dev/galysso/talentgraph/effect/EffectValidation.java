package dev.galysso.talentgraph.effect;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The context an effect is validated in: the talent's rank count, the
 * loaded assets, and where to send warnings. Effect faults are always
 * warnings: the graph stays playable, the faulty effect is dropped or
 * repaired, and the node shows the badge.
 *
 * <p>The checks shared by several types live here so their messages read
 * the same everywhere.</p>
 */
public final class EffectValidation {

    private final int maxRank;
    private final References refs;
    private final Consumer<String> warnings;

    /**
     * @param maxRank  the talent's {@code "MaxRank"}, already repaired
     * @param refs     the loaded assets
     * @param warnings receives each fault, already prefixed with the effect
     */
    public EffectValidation(int maxRank, References refs, Consumer<String> warnings) {
        this.maxRank = maxRank;
        this.refs = refs;
        this.warnings = warnings;
    }

    public int maxRank() {
        return maxRank;
    }

    public References refs() {
        return refs;
    }

    public void warn(String message) {
        warnings.accept(message);
    }

    /**
     * Checks a required per-rank list.
     *
     * @return false when there is no usable value
     */
    public boolean amounts(String field, @Nullable Amounts amounts) {
        if (amounts == null) {
            warn("\"" + field + "\" is missing; the effect is ignored");
            return false;
        }
        if (amounts.isEmpty()) {
            warn("\"" + field + "\" is empty; the effect is ignored");
            return false;
        }
        if (amounts.size() > maxRank) {
            warn("\"" + field + "\" has " + amounts.size() + " values but \"MaxRank\" is " + maxRank
                    + "; the extra values are never used");
        }
        return true;
    }

    /** {@return the calculation written, {@link Calculation#ADDITIVE} when absent or unknown} */
    public Calculation calculation(@Nullable String written) {
        if (written == null || written.isBlank()) {
            return Calculation.ADDITIVE;
        }
        Calculation parsed = Calculation.parse(written);
        if (parsed == null) {
            warn("\"Calculation\" must be \"Additive\" or \"Multiplicative\", got \"" + written + "\"; using Additive");
            return Calculation.ADDITIVE;
        }
        return parsed;
    }

    /**
     * Resolves an item list, dropping the entries that name nothing.
     *
     * @param field the field name, for messages
     * @param items the entries as written, possibly null
     * @return the entries that resolved, in order
     */
    public List<ItemMatcher> items(String field, @Nullable String[] items) {
        List<ItemMatcher> resolved = new ArrayList<>();
        if (items == null) {
            return resolved;
        }
        for (int i = 0; i < items.length; i++) {
            String written = items[i];
            if (written == null || written.isBlank()) {
                warn("\"" + field + "\" has an empty entry at index " + i);
                continue;
            }
            ItemMatcher matcher = ItemMatcher.resolve(written, refs);
            if (matcher == null) {
                warn("\"" + field + "\": \"" + written + "\" is neither an item id nor a tag any item carries; skipped");
                continue;
            }
            resolved.add(matcher);
        }
        return resolved;
    }

    /** {@return whether a required id is present, warning otherwise} */
    public boolean present(String field, @Nullable String id) {
        if (id == null || id.isBlank()) {
            warn("\"" + field + "\" is missing; the effect is ignored");
            return false;
        }
        return true;
    }
}
