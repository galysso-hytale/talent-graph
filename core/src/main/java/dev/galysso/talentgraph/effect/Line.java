package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.protocol.InteractionType;

import javax.annotation.Nullable;
import java.util.Comparator;

/**
 * One effect of a talent, put into words for one player: what the tooltip
 * and the detail panel show, already in the player's language. Produced by
 * {@link TalentEffect#describe} through {@link EffectDescriber}.
 *
 * <p>The text comes in three parts so that the panel can line up the
 * ranks: a fixed {@code prefix} (the key of an ability), the {@code value}
 * that changes with the rank ({@code "+20"}, the name of the rank's
 * effect), and a fixed {@code suffix} (the stat, the conditions). The
 * tooltip joins them; the panel lists the values of every rank.</p>
 *
 * @param category which heading the line goes under
 * @param sign     gain, loss, or inactive
 * @param prefix   fixed text before the value, possibly empty
 * @param value    the part that depends on the rank, possibly empty
 * @param suffix   fixed text after the value, possibly empty
 * @param order    position among the lines of its sign and category; lines of
 *                 equal order sort alphabetically in the player's language
 * @param slot     for an ability, the slot its key belongs to; null otherwise
 * @param fromRank the first rank the effect applies at
 * @param active   whether the effect applies at the rank described
 */
public record Line(Category category, Sign sign, String prefix, String value, String suffix, int order,
                   @Nullable InteractionType slot, int fromRank, boolean active) {

    /** Order for lines that have no natural one: alphabetical only. */
    public static final int NO_ORDER = Integer.MAX_VALUE;

    /** {@return the whole line, the non-empty parts separated by spaces} */
    public String text() {
        StringBuilder text = new StringBuilder();
        for (String part : new String[] {prefix, value, suffix}) {
            if (part != null && !part.isEmpty()) {
                if (!text.isEmpty()) {
                    text.append(' ');
                }
                text.append(part);
            }
        }
        return text.toString();
    }

    /** {@return the line without its prefix, when the key is shown as a glyph beside it} */
    public String textWithoutPrefix() {
        return new Line(category, sign, "", value, suffix, order, slot, fromRank, active).text();
    }

    /** {@return the same line with another value, for the card's "+10 -> +20"} */
    public Line withValue(String newValue) {
        return new Line(category, sign, prefix, newValue, suffix, order, slot, fromRank, active);
    }

    /**
     * {@return the display order: category, then gains before losses
     * before inactive lines, then the natural order, then the alphabet
     * of the given language}
     */
    public static Comparator<Line> comparator(Comparator<String> alphabet) {
        return Comparator.comparing(Line::category)
                .thenComparing(Line::sign)
                .thenComparingInt(Line::order)
                .thenComparing(Line::text, alphabet);
    }

    /** The headings, in the order they are shown. */
    public enum Category {
        STATS("Stats"), MOVEMENT("Movement"), EFFECTS("Effects"), EQUIPMENT("Equipment"), ABILITIES("Abilities");

        private final String key;

        Category(String key) {
            this.key = key;
        }

        /** {@return the translation key of the heading} */
        public String key() {
            return "talentgraph.category." + key;
        }
    }

    /** What the colour of a line says. */
    public enum Sign {
        /** Green: the player gains something. */
        GAIN,
        /** Red: the player loses something. */
        LOSS,
        /** Grey: not in effect at the rank described. */
        INACTIVE
    }
}
