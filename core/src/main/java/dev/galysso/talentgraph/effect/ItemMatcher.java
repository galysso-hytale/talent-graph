package dev.galysso.talentgraph.effect;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * One entry of an item list in a graph file, resolved: either an item id
 * ({@code "Weapon_Sword_Wood"}) or a tag every matching item carries
 * ({@code "Family=Sword"}, {@code "Type=Weapon"}, {@code "Weapon"}).
 *
 * <p>Tags win over ids when both exist under the same spelling, which
 * cannot happen with vanilla naming (ids have no {@code =} and tags are
 * single words that no item id reuses).</p>
 *
 * @param written  the entry as written, for messages
 * @param itemId   the item id, or null for a tag
 * @param tagIndex the tag index, or {@link Integer#MIN_VALUE} for an item
 */
public record ItemMatcher(String written, @Nullable String itemId, int tagIndex) {

    public ItemMatcher {
        Objects.requireNonNull(written, "written");
    }

    /**
     * Resolves an entry against the loaded assets.
     *
     * @return the matcher, or null when neither an item nor a tag is known
     */
    @Nullable
    public static ItemMatcher resolve(String written, References refs) {
        int tag = refs.itemTagIndex(written);
        if (tag != Integer.MIN_VALUE) {
            return new ItemMatcher(written, null, tag);
        }
        if (refs.hasItem(written)) {
            return new ItemMatcher(written, written, Integer.MIN_VALUE);
        }
        return null;
    }

    public boolean isTag() {
        return itemId == null;
    }
}
