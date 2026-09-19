package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.server.core.modules.i18n.I18nModule;

import javax.annotation.Nullable;
import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

/**
 * The words of one player's language, as the effect descriptions need
 * them: a lookup in the server's translation tables and the collation
 * that sorts the lines shown in that language.
 *
 * <p>The mod ships its own strings in
 * {@code Server/Languages/en-US/talentgraph.lang}, read by the server like
 * any pack's; each key is the file name then the line's key
 * ({@code talentgraph.stat.Health}). A pack can add other languages, or
 * translate what it defines itself under the keys the descriptions look
 * up first (see {@code docs/EFFECTS.md}, "Tooltips").</p>
 *
 * <p>An interface so that the descriptions run without a server: the
 * harness gives them a table of its own.</p>
 */
public interface Texts {

    /** {@return the translation of a key in this language, or null when no table has it} */
    @Nullable
    String lookup(String key);

    /** {@return the language, as a locale, for case mapping and collation} */
    Locale locale();

    /** {@return the alphabetical order of this language} */
    Comparator<String> order();

    /**
     * {@return the translation of a key with its {@code {name}} parameters
     * filled} A missing key shows as {@code [key]}: a translation gap is
     * a visible bug, never a crash.
     *
     * @param pairs parameter names and values, alternating
     */
    default String get(String key, String... pairs) {
        String text = lookup(key);
        if (text == null) {
            text = "[" + key + "]";
        }
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            text = text.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return text;
    }

    /**
     * {@return a text an admin wrote, translated when it is a key of the
     * tables, as written otherwise} This is how a pack's {@code "Name"},
     * {@code "Description"} and {@code "Details"} become translatable
     * without a second field.
     */
    @Nullable
    default String keyOrRaw(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String translated = lookup(text.trim());
        return translated != null ? translated : text;
    }

    /** {@return the words of a player's language, {@code en-US} when unknown} */
    static Texts of(@Nullable String language) {
        String lang = language == null || language.isBlank() ? "en-US" : language;
        Locale locale = Locale.forLanguageTag(lang);
        Comparator<String> collator = Collator.getInstance(locale)::compare;
        return new Texts() {
            @Override
            public String lookup(String key) {
                I18nModule i18n = I18nModule.get();
                return i18n == null ? null : i18n.getMessage(lang, key);
            }

            @Override
            public Locale locale() {
                return locale;
            }

            @Override
            public Comparator<String> order() {
                return collator;
            }
        };
    }
}
