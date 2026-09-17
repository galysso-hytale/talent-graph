package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.lookup.CodecMapCodec;
import com.hypixel.hytale.codec.lookup.Priority;

import java.util.List;

/**
 * The registry of effect types: {@code "Type"} to codec, the same shape as
 * Hytale's {@code Interaction.CODEC}. Internal for now; a type registered
 * by another plugin is a later extension.
 *
 * <p>An unregistered type falls back to {@link UnknownEffect} (registered
 * as the default, lowest priority) rather than throwing, so that the file
 * still loads and the node reports the typo.</p>
 */
public final class EffectTypes {

    /** Decodes one entry of {@code "Effects"} by its {@code "Type"}. */
    public static final CodecMapCodec<TalentEffect> CODEC = new CodecMapCodec<TalentEffect>("Type", true)
            .register(Priority.DEFAULT, "?", UnknownEffect.class, UnknownEffect.CODEC)
            .register(StatEffect.TYPE, StatEffect.class, StatEffect.CODEC)
            .register(EntityEffectLink.TYPE, EntityEffectLink.class, EntityEffectLink.CODEC)
            .register(EquipmentEffect.TYPE, EquipmentEffect.class, EquipmentEffect.CODEC)
            .register(AbilityEffect.TYPE, AbilityEffect.class, AbilityEffect.CODEC)
            .register(MovementEffect.TYPE, MovementEffect.class, MovementEffect.CODEC);

    /** Decodes the whole {@code "Effects"} list. */
    public static final ArrayCodec<TalentEffect> LIST_CODEC = new ArrayCodec<>(CODEC, TalentEffect[]::new);

    private static final List<String> IDS = List.of(StatEffect.TYPE, EntityEffectLink.TYPE, EquipmentEffect.TYPE,
            AbilityEffect.TYPE, MovementEffect.TYPE);

    private EffectTypes() {
    }

    /** {@return the supported {@code "Type"} values, in documentation order} */
    public static List<String> ids() {
        return IDS;
    }
}
