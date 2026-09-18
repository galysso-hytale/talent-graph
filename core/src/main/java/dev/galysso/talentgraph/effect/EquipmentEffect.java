package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nullable;
import java.util.List;

/**
 * {@code "Type": "Equipment"} — allows or forbids using classes of items,
 * on top of the graph's {@code "Equipment"} baseline.
 *
 * <pre>{@code
 * { "Type": "Equipment", "Mode": "Allow",  "Items": ["Family=Sword", "Family=Axe"] }
 * { "Type": "Equipment", "Mode": "Forbid", "Items": ["Family=Shortbow"], "Priority": 1 }
 * }</pre>
 *
 * <p>Resolution for one item ({@link EquipmentRules}): rules of unlocked
 * talents beat the baseline; between talents the highest {@code "Priority"}
 * wins (default 0); on a tie a rule naming the item by id beats one naming
 * a tag; still tied, {@code Forbid} wins. An {@code Allow} forbids nothing
 * by itself: it opens what the baseline or a {@code Forbid} closes.</p>
 */
public final class EquipmentEffect extends TalentEffect {

    public static final String TYPE = "Equipment";

    public static final BuilderCodec<EquipmentEffect> CODEC = BuilderCodec.builder(
                    EquipmentEffect.class, EquipmentEffect::new, BASE_CODEC)
            .append(new KeyedCodec<>("Mode", Codec.STRING, false), (e, v) -> e.modeText = v, e -> e.modeText).add()
            .append(new KeyedCodec<>("Items", Codec.STRING_ARRAY, false), (e, v) -> e.itemsText = v, e -> e.itemsText).add()
            .append(new KeyedCodec<>("Priority", Codec.INTEGER, false), (e, v) -> e.priority = v, e -> e.priority).add()
            .build();

    @Nullable
    private String modeText;
    @Nullable
    private String[] itemsText;
    private int priority;
    private Mode mode;
    private List<ItemMatcher> items = List.of();

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    protected boolean check(EffectValidation v) {
        if (!v.present("Mode", modeText)) {
            return false;
        }
        mode = Mode.parse(modeText);
        if (mode == null) {
            v.warn("\"Mode\" must be \"Allow\" or \"Forbid\", got \"" + modeText + "\"; the effect is ignored");
            return false;
        }
        if (itemsText == null || itemsText.length == 0) {
            v.warn("\"Items\" is missing or empty; the effect is ignored");
            return false;
        }
        items = List.copyOf(v.items("Items", itemsText));
        if (items.isEmpty()) {
            v.warn("No entry of \"Items\" could be resolved; the effect is ignored");
            return false;
        }
        return true;
    }

    public Mode mode() {
        return mode;
    }

    /** {@return the resolved entries, never empty after validation} */
    public List<ItemMatcher> items() {
        return items;
    }

    public int priority() {
        return priority;
    }

    /** Whether the listed items become usable or unusable. */
    public enum Mode {
        ALLOW("Allow"), FORBID("Forbid");

        private final String id;

        Mode(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        @Nullable
        static Mode parse(String text) {
            for (Mode m : values()) {
                if (m.id.equalsIgnoreCase(text)) {
                    return m;
                }
            }
            return null;
        }
    }
}
