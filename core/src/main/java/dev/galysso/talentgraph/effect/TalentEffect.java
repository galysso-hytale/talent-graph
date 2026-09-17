package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * One entry of a talent's {@code "Effects"} list, as written in the graph
 * file. Each subclass is one {@code "Type"} (see {@link EffectTypes}) and
 * only holds data: what the effect does to a player is the engine's job.
 *
 * <p>The values are read as loosely as the codecs allow ({@code "Calculation"}
 * is a string, not an enum) so that a typo becomes a warning on the node
 * through {@link #validate}, never a file the asset store rejects. After
 * validation an instance is treated as read-only.</p>
 */
public abstract class TalentEffect {

    /** Fields shared by every type; each subclass codec chains on this one. */
    public static final BuilderCodec<TalentEffect> BASE_CODEC = BuilderCodec.abstractBuilder(TalentEffect.class)
            // Rank from which the effect applies; below it, nothing is granted.
            .append(new KeyedCodec<>("FromRank", Codec.INTEGER, false),
                    (e, v) -> e.fromRank = v, e -> e.fromRank).add()
            .build();

    protected int fromRank = 1;

    /** {@return the {@code "Type"} this effect was registered under} */
    public abstract String type();

    /**
     * Checks the values against the loaded assets and repairs what can be,
     * reporting each fault on the validation. Called once, after every asset
     * store is loaded, so unknown references are real.
     *
     * @return false when the effect is unusable and must be dropped
     */
    public final boolean validate(EffectValidation v) {
        if (fromRank < 1) {
            v.warn("\"FromRank\" must be at least 1, got " + fromRank + "; using 1");
            fromRank = 1;
        } else if (fromRank > v.maxRank()) {
            v.warn("\"FromRank\" is " + fromRank + " but \"MaxRank\" is " + v.maxRank() + "; the effect never applies");
            return false;
        }
        return check(v);
    }

    /** Type-specific part of {@link #validate}. */
    protected abstract boolean check(EffectValidation v);

    /** {@return the first rank at which the effect applies, at least 1} */
    public int fromRank() {
        return fromRank;
    }

    /** {@return whether the effect applies at this rank} */
    public boolean appliesAt(int rank) {
        return rank >= fromRank;
    }
}
