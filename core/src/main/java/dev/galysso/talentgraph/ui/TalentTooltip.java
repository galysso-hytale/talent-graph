package dev.galysso.talentgraph.ui;

import com.hypixel.hytale.server.core.Message;
import dev.galysso.talentgraph.effect.EffectDescriber;
import dev.galysso.talentgraph.effect.Line;
import dev.galysso.talentgraph.effect.References;
import dev.galysso.talentgraph.effect.TalentEffect;
import dev.galysso.talentgraph.effect.Texts;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the lines of {@link EffectDescriber} into the coloured text of the
 * hover card and of the detail panel, for one player: green for gains, red
 * for losses, grey for what the rank does not give yet, headings in the
 * capitals of the player's language.
 *
 * <p>The rank described is the next one while the talent can still grow
 * (what the click gives), the current one once maxed; a line whose value
 * changes reads {@code "+10 -> +20"} on the card, and the panel lists the
 * value of every rank with the current one in bold.</p>
 */
final class TalentTooltip {

    static final String COLOR_TITLE = "#f0f4ff";
    static final String COLOR_MUTED = "#96a9be";
    static final String COLOR_GAIN = "#3fa86f";
    static final String COLOR_LOSS = "#e05a5a";
    static final String COLOR_INACTIVE = "#6b7a8c";
    static final String COLOR_LORE = "#c9d4e0";

    private final EffectDescriber describer;
    private final Texts texts;

    TalentTooltip(References refs, Texts texts) {
        this.describer = new EffectDescriber(refs, texts);
        this.texts = texts;
    }

    Texts texts() {
        return texts;
    }

    /** {@return the rank the tooltip describes: the next one while the talent can grow} */
    static int shownRank(int rank, int maxRank) {
        return rank < maxRank ? rank + 1 : Math.max(rank, 1);
    }

    /** {@return the lines of a talent for the hover card, sorted, at the rank the card describes} */
    List<Line> lines(List<TalentEffect> effects, int rank, int maxRank) {
        return describer.describe(effects, shownRank(rank, maxRank), rank);
    }

    /** {@return a text a pack wrote, translated when it is a key; null when absent} */
    @Nullable
    String written(@Nullable String text) {
        return texts.keyOrRaw(text);
    }

    // ---- rows ----

    /**
     * One row of a card or panel body: the sorted line, its text, the
     * value the player has now when the rank changes it (shown before an
     * arrow), and its notes. An ability row leaves out its key, shown as a
     * glyph or key cap beside it.
     *
     * @param line     the line at the rank shown
     * @param name     the text, coloured
     * @param previous the current value, or null when nothing changes
     * @param notes    the conditions, one per line under the text
     */
    record Row(Line line, Message name, @Nullable String previous, List<Line.Note> notes) {
    }

    /** {@return the rows of the hover card for the lines of {@link #lines}} */
    List<Row> rows(List<Line> lines) {
        List<Row> rows = new ArrayList<>();
        for (Line line : lines) {
            String text = line.slot() == null ? line.text() : line.textWithoutPrefix();
            Message name = Message.empty().insert(Message.raw(text).color(color(line.sign())));
            if (!line.active()) {
                name.insert(Message.raw(" " + fromRank(line)).color(COLOR_INACTIVE));
            }
            rows.add(new Row(line, name, line.previous(), line.notes()));
        }
        return rows;
    }

    /** {@return the heading of a category, in the capitals of the player's language} */
    String heading(Line.Category category) {
        return texts.get(category.key()).toUpperCase(texts.locale());
    }

    private String fromRank(Line line) {
        return describer.get("tooltip.fromRank", "rank", String.valueOf(line.fromRank()));
    }

    static String color(Line.Sign sign) {
        return switch (sign) {
            case GAIN -> COLOR_GAIN;
            case LOSS -> COLOR_LOSS;
            case INACTIVE -> COLOR_INACTIVE;
        };
    }

    // ---- panel ----

    /**
     * {@return the rows of the detail panel: each effect as it applies at
     * the rank shown, with the values of every rank when they differ, in
     * the order of the card}
     */
    List<Row> panelRows(List<TalentEffect> effects, int rank, int maxRank) {
        int shown = shownRank(rank, maxRank);
        List<Row> rows = new ArrayList<>();
        for (TalentEffect effect : effects) {
            Line line = describer.describe(effect, shown, shown);
            if (line != null) {
                rows.add(new Row(line, panelText(effect, line, rank, maxRank), null, line.notes()));
            }
        }
        rows.sort((a, b) -> Line.comparator(texts.order()).compare(a.line, b.line));
        return rows;
    }

    private Message panelText(TalentEffect effect, Line line, int rank, int maxRank) {
        String color = color(line.sign());
        Message text = Message.empty();
        // The value of each rank, when it changes with the rank.
        List<String> values = new ArrayList<>();
        boolean varies = false;
        for (int r = 1; r <= maxRank; r++) {
            Line at = effect.appliesAt(r) ? describer.describe(effect, r, r) : null;
            String value = at == null ? null : at.value();
            values.add(value);
            varies |= value != null && !value.equals(line.value());
        }
        if (!varies || line.value().isEmpty()) {
            if (!line.value().isEmpty()) {
                text.insert(Message.raw(line.value()).color(color));
            }
        } else {
            String separator = describer.get("panel.rankSeparator");
            boolean first = true;
            for (int r = 1; r <= maxRank; r++) {
                String value = values.get(r - 1);
                if (value == null) {
                    continue;
                }
                if (!first) {
                    text.insert(Message.raw(separator).color(COLOR_MUTED));
                }
                first = false;
                text.insert(Message.raw(value).color(color).bold(r == rank));
            }
        }
        if (!line.suffix().isEmpty()) {
            text.insert(Message.raw((line.value().isEmpty() ? "" : " ") + line.suffix()).color(color));
        }
        if (line.fromRank() > 1) {
            text.insert(Message.raw(" " + fromRank(line)).color(COLOR_INACTIVE));
        }
        return text;
    }
}
