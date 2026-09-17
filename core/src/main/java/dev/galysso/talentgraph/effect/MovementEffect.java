package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nullable;

/**
 * {@code "Type": "Movement"} — modifies one of the player's movement
 * settings, from the closed list of {@link MovementSetting}.
 *
 * <pre>{@code
 * { "Type": "Movement", "Setting": "JumpForce", "Amount": 1.1, "Calculation": "Multiplicative" }
 * }</pre>
 *
 * <p>Speed settings shift the balance of combat and exploration; small
 * values are the rule.</p>
 */
public final class MovementEffect extends TalentEffect {

    public static final String TYPE = "Movement";

    public static final BuilderCodec<MovementEffect> CODEC = BuilderCodec.builder(
                    MovementEffect.class, MovementEffect::new, BASE_CODEC)
            .append(new KeyedCodec<>("Setting", Codec.STRING, false), (e, v) -> e.settingText = v, e -> e.settingText).add()
            .append(new KeyedCodec<>("Amount", Amounts.CODEC, false), (e, v) -> e.amount = v, e -> e.amount).add()
            .append(new KeyedCodec<>("Calculation", Codec.STRING, false),
                    (e, v) -> e.calculationText = v, e -> e.calculationText).add()
            .build();

    @Nullable
    private String settingText;
    private Amounts amount;
    @Nullable
    private String calculationText;
    private MovementSetting setting;
    private Calculation calculation = Calculation.ADDITIVE;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    protected boolean check(EffectValidation v) {
        if (!v.present("Setting", settingText) || !v.amounts("Amount", amount)) {
            return false;
        }
        setting = MovementSetting.parse(settingText);
        if (setting == null) {
            v.warn("Unknown movement setting \"" + settingText + "\"; the effect is ignored (see docs/EFFECTS.md for the list)");
            return false;
        }
        calculation = v.calculation(calculationText);
        return true;
    }

    public MovementSetting setting() {
        return setting;
    }

    public Amounts amount() {
        return amount;
    }

    public Calculation calculation() {
        return calculation;
    }
}
