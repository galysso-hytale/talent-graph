package dev.galysso.talentgraph.effect;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntPredicate;

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

    /**
     * {@return whether an item is one this entry names}
     *
     * @param itemId the item id
     * @param hasTag whether the item carries a tag index
     */
    public boolean matches(String itemId, IntPredicate hasTag) {
        return isTag() ? hasTag.test(tagIndex) : this.itemId.equals(itemId);
    }

    /** {@return whether at least one loaded item is named by both entries} */
    public boolean overlaps(ItemMatcher other, References refs) {
        if (!isTag()) {
            return other.isTag() ? refs.itemsWithTag(other.tagIndex).contains(itemId) : itemId.equals(other.itemId);
        }
        if (!other.isTag()) {
            return refs.itemsWithTag(tagIndex).contains(other.itemId);
        }
        Set<String> mine = refs.itemsWithTag(tagIndex);
        Set<String> theirs = refs.itemsWithTag(other.tagIndex);
        if (mine.size() > theirs.size()) {
            Set<String> swap = mine;
            mine = theirs;
            theirs = swap;
        }
        for (String id : mine) {
            if (theirs.contains(id)) {
                return true;
            }
        }
        return false;
    }
}
