package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * The graph-level {@code "Equipment"} section: what is unusable for
 * everyone until a talent opens it, and the exceptions cut into that.
 *
 * <pre>{@code
 * "Equipment": {
 *     "Forbidden": ["Type=Weapon", "Type=Armor"],
 *     "Allowed":   ["Family=Sword", "Armor_Leather_Chest"]
 * }
 * }</pre>
 *
 * <p>{@code Allowed} beats {@code Forbidden} here: listing an exception has
 * no other meaning. An item that matches neither is free. Across loaded
 * graphs the lists are unioned. Without the section nothing is forbidden
 * at the start, and only {@code Forbid} talent effects close anything.</p>
 */
public final class EquipmentBaseline {

    public static final BuilderCodec<EquipmentBaseline> CODEC = BuilderCodec.builder(
                    EquipmentBaseline.class, EquipmentBaseline::new)
            .append(new KeyedCodec<>("Forbidden", Codec.STRING_ARRAY, false),
                    (b, v) -> b.forbiddenText = v, b -> b.forbiddenText).add()
            .append(new KeyedCodec<>("Allowed", Codec.STRING_ARRAY, false),
                    (b, v) -> b.allowedText = v, b -> b.allowedText).add()
            .build();

    /** The baseline of a graph without an {@code "Equipment"} section. */
    public static final EquipmentBaseline NONE = new EquipmentBaseline();

    @Nullable
    private String[] forbiddenText;
    @Nullable
    private String[] allowedText;
    private List<ItemMatcher> forbidden = List.of();
    private List<ItemMatcher> allowed = List.of();

    /**
     * Resolves both lists against the loaded assets, dropping the entries
     * that name nothing.
     *
     * @param refs     the loaded assets
     * @param warnings receives each fault, phrased for the whole graph
     */
    public void validate(References refs, Consumer<String> warnings) {
        EffectValidation v = new EffectValidation(1, refs, message -> warnings.accept("\"Equipment\": " + message));
        forbidden = List.copyOf(v.items("Forbidden", forbiddenText));
        allowed = List.copyOf(v.items("Allowed", allowedText));
        if (forbiddenText != null && forbiddenText.length == 0 && (allowedText == null || allowedText.length == 0)) {
            v.warn("the section is empty and has no effect");
        } else if (!allowed.isEmpty() && forbidden.isEmpty()) {
            v.warn("\"Allowed\" without \"Forbidden\" has no effect: nothing is forbidden to begin with");
        }
    }

    /** {@return what is unusable until a talent allows it} */
    public List<ItemMatcher> forbidden() {
        return forbidden;
    }

    /** {@return the exceptions to {@link #forbidden()}} */
    public List<ItemMatcher> allowed() {
        return allowed;
    }

    public boolean isEmpty() {
        return forbidden.isEmpty() && allowed.isEmpty();
    }
}
