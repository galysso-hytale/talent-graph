package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;

import java.util.Set;

/**
 * What the server knows, as far as effects need it. One implementation
 * reads the live asset maps; the interface exists so the loader can be
 * exercised without a running server.
 *
 * <p>Every lookup is by exact id, as Hytale's own references are. The
 * answers are only meaningful once every asset pack is loaded, which is why
 * graphs are validated after {@code LoadAssetEvent} rather than as their
 * own store loads them.</p>
 */
public interface References {

    /** Reads the asset maps of the running server. */
    References LIVE = new References() {
        @Override
        public boolean hasStat(String id) {
            return EntityStatType.getAssetMap().getIndex(id) != Integer.MIN_VALUE;
        }

        @Override
        public boolean hasEntityEffect(String id) {
            return EntityEffect.getAssetMap().getIndex(id) != Integer.MIN_VALUE;
        }

        @Override
        public boolean hasRootInteraction(String id) {
            return RootInteraction.getAssetMap().getIndex(id) != Integer.MIN_VALUE;
        }

        @Override
        public boolean hasItem(String id) {
            return Item.getAssetMap().getAsset(id) != null;
        }

        @Override
        public int itemTagIndex(String tag) {
            int index = AssetRegistry.getTagIndex(tag);
            if (index == Integer.MIN_VALUE || Item.getAssetMap().getKeysForTag(index).isEmpty()) {
                return Integer.MIN_VALUE;
            }
            return index;
        }

        @Override
        public Set<String> itemsWithTag(int tagIndex) {
            return Item.getAssetMap().getKeysForTag(tagIndex);
        }
    };

    /** {@return whether an {@code EntityStatType} of this id is loaded} */
    boolean hasStat(String id);

    /** {@return whether an {@code EntityEffect} of this id is loaded} */
    boolean hasEntityEffect(String id);

    /** {@return whether a {@code RootInteraction} of this id is loaded} */
    boolean hasRootInteraction(String id);

    /** {@return whether an {@code Item} of this id is loaded} */
    boolean hasItem(String id);

    /**
     * {@return the index of a tag at least one item carries, or
     * {@link Integer#MIN_VALUE}} Tags are the expanded form items carry:
     * {@code "Weapon"}, {@code "Type=Weapon"}, {@code "Family=Sword"}.
     */
    int itemTagIndex(String tag);

    /** {@return the ids of the items carrying a tag index, empty if none} */
    Set<String> itemsWithTag(int tagIndex);
}
