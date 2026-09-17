package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nullable;

/**
 * {@code "Type": "Stat"} — modifies the maximum (or minimum) of an entity
 * stat such as {@code Health} or {@code Stamina}.
 *
 * <pre>{@code
 * { "Type": "Stat", "Stat": "Health", "Amount": [10, 20, 35] }
 * { "Type": "Stat", "Stat": "Stamina", "Amount": 0.9, "Calculation": "Multiplicative" }
 * }</pre>
 *
 * <p>Any {@code EntityStatType} the server has loaded is accepted, so a
 * pack's own stats work too; see {@code docs/EFFECTS.md} for the vanilla
 * ones.</p>
 */
public final class StatEffect extends TalentEffect {

    public static final String TYPE = "Stat";

    public static final BuilderCodec<StatEffect> CODEC = BuilderCodec.builder(
                    StatEffect.class, StatEffect::new, BASE_CODEC)
            .append(new KeyedCodec<>("Stat", Codec.STRING, false), (e, v) -> e.stat = v, e -> e.stat).add()
            .append(new KeyedCodec<>("Amount", Amounts.CODEC, false), (e, v) -> e.amount = v, e -> e.amount).add()
            .append(new KeyedCodec<>("Calculation", Codec.STRING, false),
                    (e, v) -> e.calculationText = v, e -> e.calculationText).add()
            .append(new KeyedCodec<>("Target", Codec.STRING, false), (e, v) -> e.targetText = v, e -> e.targetText).add()
            .build();

    private String stat;
    private Amounts amount;
    @Nullable
    private String calculationText;
    @Nullable
    private String targetText;
    private Calculation calculation = Calculation.ADDITIVE;
    private Target target = Target.MAX;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    protected boolean check(EffectValidation v) {
        if (!v.present("Stat", stat) || !v.amounts("Amount", amount)) {
            return false;
        }
        if (!v.refs().hasStat(stat)) {
            v.warn("Unknown stat \"" + stat + "\"; the effect is ignored (see docs/EFFECTS.md for the list)");
            return false;
        }
        calculation = v.calculation(calculationText);
        target = Target.MAX;
        if (targetText != null && !targetText.isBlank()) {
            Target parsed = Target.parse(targetText);
            if (parsed == null) {
                v.warn("\"Target\" must be \"Max\" or \"Min\", got \"" + targetText + "\"; using Max");
            } else {
                target = parsed;
            }
        }
        if (calculation == Calculation.MULTIPLICATIVE) {
            for (double value : amount.values()) {
                if (value <= 0) {
                    v.warn("A multiplicative \"Amount\" of " + value + " sets the stat to nothing or below");
                    break;
                }
            }
        }
        return true;
    }

    public String stat() {
        return stat;
    }

    public Amounts amount() {
        return amount;
    }

    public Calculation calculation() {
        return calculation;
    }

    public Target target() {
        return target;
    }

    /** Which bound of the stat the modifier moves. */
    public enum Target {
        MAX("Max"), MIN("Min");

        private final String id;

        Target(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        @Nullable
        static Target parse(String text) {
            for (Target t : values()) {
                if (t.id.equalsIgnoreCase(text)) {
                    return t;
                }
            }
            return null;
        }
    }
}
