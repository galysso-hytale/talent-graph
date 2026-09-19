package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nullable;

/**
 * What an effect with an unregistered {@code "Type"} decodes to, so that a
 * typo is reported on the node instead of failing the whole file. Always
 * dropped by validation.
 */
public final class UnknownEffect extends TalentEffect {

    public static final BuilderCodec<UnknownEffect> CODEC = BuilderCodec.builder(
                    UnknownEffect.class, UnknownEffect::new, BASE_CODEC)
            .append(new KeyedCodec<>("Type", Codec.STRING, false), (e, v) -> e.written = v, e -> e.written).add()
            .build();

    @Nullable
    private String written;

    @Override
    public String type() {
        return written == null ? "?" : written;
    }

    @Override
    protected Line describe(EffectDescriber d, int rank) {
        return null; // never survives validation
    }

    @Override
    protected boolean check(EffectValidation v) {
        v.warn((written == null || written.isBlank() ? "No \"Type\"" : "Unknown effect type \"" + written + "\"")
                + "; supported: " + String.join(", ", EffectTypes.ids()) + " (see docs/EFFECTS.md)");
        return false;
    }
}
