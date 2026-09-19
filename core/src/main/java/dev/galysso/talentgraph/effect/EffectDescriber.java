package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.protocol.InteractionType;

import javax.annotation.Nullable;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Puts the effects of a talent into words for one player, in that
 * player's language. Each {@link TalentEffect} writes its own line
 * ({@link TalentEffect#describe}); this class gives it the words and the
 * facts it needs, then sorts the lines and marks what the rank changes.
 *
 * <p>Pure: it reads {@link References} and {@link Texts} and touches
 * nothing else, so the harness can run it with fakes of both. Every string
 * a player sees comes from the translation tables through {@link Texts},
 * except what a pack wrote itself and did not translate.</p>
 */
public final class EffectDescriber {

    private static final String PREFIX = "talentgraph.";

    private final References refs;
    private final Texts texts;
    private final NumberFormat numbers;

    public EffectDescriber(References refs, Texts texts) {
        this.refs = refs;
        this.texts = texts;
        this.numbers = NumberFormat.getNumberInstance(texts.locale());
        numbers.setGroupingUsed(false);
        numbers.setMaximumFractionDigits(3);
    }

    public References refs() {
        return refs;
    }

    public Texts texts() {
        return texts;
    }

    /**
     * Describes the effects of a talent at a rank, sorted for display.
     *
     * @param effects     the validated effects of the talent
     * @param shownRank   the rank described: the next one while the talent can
     *                    still grow, the current one otherwise
     * @param currentRank the player's rank, 0 when locked
     */
    public List<Line> describe(List<TalentEffect> effects, int shownRank, int currentRank) {
        List<Line> lines = new ArrayList<>();
        for (TalentEffect effect : effects) {
            Line line = describe(effect, shownRank, currentRank);
            if (line != null) {
                lines.add(line);
            }
        }
        lines.sort(Line.comparator(texts.order()));
        return lines;
    }

    /**
     * Describes one effect at a rank. An effect that does not apply yet at
     * that rank gives an inactive line; one whose value changes between
     * the player's rank and the rank shown remembers the current value
     * ({@link Line#previous}), which the card shows before an arrow.
     *
     * @return the line, or null for an effect with nothing to say
     */
    @Nullable
    public Line describe(TalentEffect effect, int shownRank, int currentRank) {
        Line line = effect.describe(this, Math.max(shownRank, effect.fromRank()));
        if (line == null) {
            return null;
        }
        if (!effect.appliesAt(shownRank)) {
            return new Line(line.category(), Line.Sign.INACTIVE, line.prefix(), line.value(), line.suffix(),
                    line.notes(), line.order(), line.slot(), effect.fromRank(), false);
        }
        if (currentRank >= 1 && currentRank < shownRank && effect.appliesAt(currentRank) && !line.value().isEmpty()) {
            Line before = effect.describe(this, currentRank);
            if (before != null && !before.value().equals(line.value())) {
                line = line.withPrevious(before.value());
            }
        }
        return line;
    }

    // ---- words ----

    /** {@return a translation of the mod, the key given without its {@code talentgraph.} prefix} */
    public String get(String key, String... pairs) {
        return texts.get(PREFIX + key, pairs);
    }

    /**
     * {@return the label of something a pack names} A {@code "Description"}
     * written on the effect wins; then a translation under {@code key}
     * (which a pack can supply for its own ids); then the fallback.
     */
    public String label(@Nullable String description, String key, String fallback) {
        String written = texts.keyOrRaw(description);
        if (written != null) {
            return written;
        }
        String translated = texts.lookup(PREFIX + key);
        return translated != null ? translated : fallback;
    }

    /** {@return the {@code "Description"} written on an effect, translated if it is a key, or the fallback} */
    public String or(@Nullable String description, String fallback) {
        String written = texts.keyOrRaw(description);
        return written != null ? written : fallback;
    }

    /** {@return an asset id as words: underscores become spaces} */
    public static String humanise(String id) {
        return id.replace('_', ' ').trim();
    }

    /** {@return a number in the player's language, without trailing zeros} */
    public String number(double value) {
        return numbers.format(value);
    }

    /** {@return the amount as shown: {@code +20}, {@code -5} or {@code ×1.2}} */
    public String amount(double value, Calculation calculation) {
        if (calculation == Calculation.MULTIPLICATIVE) {
            return "×" + number(value);
        }
        return (value >= 0 ? "+" : "-") + number(Math.abs(value));
    }

    /**
     * {@return whether the amount is a gain or a loss}
     *
     * @param higherWins whether raising the quantity is a gain
     */
    public static Line.Sign sign(double value, Calculation calculation, boolean higherWins) {
        double neutral = calculation == Calculation.MULTIPLICATIVE ? 1 : 0;
        boolean up = value > neutral;
        boolean down = value < neutral;
        if (!up && !down) {
            return Line.Sign.GAIN;
        }
        return up == higherWins ? Line.Sign.GAIN : Line.Sign.LOSS;
    }

    /** {@return a duration in seconds, as shown: {@code 12 s}} */
    public String seconds(double value) {
        return get("unit.seconds", "n", number(value));
    }

    /** {@return the default key of a slot, as the tooltip shows it (the panel shows the configured one)} */
    public String key(InteractionType slot) {
        return get("key." + slot.name());
    }

    /**
     * {@return the cost of a root interaction as words, or null when its
     * chain states none where the describer can read it} Read from a
     * {@code StatsCondition} at the top of the chain, the vanilla recipe:
     * {@code "10 mana"}, {@code "100% signature energy"}.
     */
    @Nullable
    public String cost(String rootId) {
        List<References.Cost> costs = new ArrayList<>(refs.rootCosts(rootId));
        if (costs.isEmpty()) {
            return null;
        }
        // Stats in their fixed order, as the stat lines are.
        costs.sort(Comparator.comparingInt((References.Cost c) -> StatLabels.order(c.stat()))
                .thenComparing(References.Cost::stat));
        List<String> parts = new ArrayList<>();
        for (References.Cost cost : costs) {
            String stat = label(null, "stat." + cost.stat(), humanise(cost.stat()));
            parts.add(get(cost.percent() ? "note.costPercent" : "note.costAbsolute", "n", number(cost.amount()), "stat", stat));
        }
        return join(parts, "list.and");
    }

    /** {@return a note of the given kind, the label translated} */
    public Line.Note note(String kind, String value) {
        return new Line.Note(get("note." + kind), value);
    }

    // ---- item lists ----

    /**
     * {@return the names of an item list, alphabetical in the player's
     * language, joined with the given list word}
     *
     * @param one     whether to name a kind of item in the singular ("a sword")
     * @param joinKey the translation joining two entries: {@code list.and}, {@code list.or}
     */
    public String items(List<ItemMatcher> items, boolean one, String joinKey) {
        List<String> names = new ArrayList<>();
        for (ItemMatcher item : items) {
            names.add(item(item, one));
        }
        names.sort(texts.order());
        return join(names, joinKey);
    }

    /** {@return the name of one entry: the item's translated name, or the words for a tag} */
    public String item(ItemMatcher item, boolean one) {
        if (item.isTag()) {
            String translated = texts.lookup(PREFIX + (one ? "tagOne." : "tag.") + item.written().replace('=', '.'));
            return translated != null ? translated : item.written();
        }
        String key = refs.itemTranslationKey(item.itemId());
        String translated = key == null ? null : texts.lookup(key);
        return translated != null ? translated : humanise(item.itemId());
    }

    /** {@return the names joined: separators between, the list word before the last} */
    public String join(List<String> names, String joinKey) {
        if (names.size() <= 1) {
            return names.isEmpty() ? "" : names.get(0);
        }
        StringBuilder text = new StringBuilder(names.get(0));
        String separator = get("list.separator");
        for (int i = 1; i < names.size() - 1; i++) {
            text.append(separator).append(names.get(i));
        }
        return text.append(get(joinKey)).append(names.get(names.size() - 1)).toString();
    }
}
