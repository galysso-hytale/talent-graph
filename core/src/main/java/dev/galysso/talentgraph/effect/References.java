package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.protocol.InteractionCooldown;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.UnarmedInteractions;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;

import javax.annotation.Nullable;
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

        @Override
        public String itemRootInteraction(String itemId, InteractionType slot) {
            Item item = Item.getAssetMap().getAsset(itemId);
            return item == null ? null : item.getInteractions().get(slot);
        }

        @Override
        public String unarmedRootInteraction(InteractionType slot) {
            UnarmedInteractions unarmed = UnarmedInteractions.getAssetMap().getAsset(UnarmedInteractions.DEFAULT_UNARMED_ID);
            return unarmed == null ? null : unarmed.getInteractions().get(slot);
        }

        @Override
        public String entityEffectName(String id) {
            EntityEffect effect = EntityEffect.getAssetMap().getAsset(id);
            return effect == null ? null : effect.getName();
        }

        @Override
        public boolean isDebuff(String id) {
            EntityEffect effect = EntityEffect.getAssetMap().getAsset(id);
            return effect != null && effect.isDebuff();
        }

        @Override
        public boolean hasApplyConditions(String id) {
            EntityEffect effect = EntityEffect.getAssetMap().getAsset(id);
            return effect != null && effect.getApplyConditions() != null && effect.getApplyConditions().length > 0;
        }

        @Override
        public double rootCooldown(String id) {
            RootInteraction root = RootInteraction.getAssetMap().getAsset(id);
            InteractionCooldown cooldown = root == null ? null : root.getCooldown();
            return cooldown == null ? 0 : cooldown.cooldown;
        }

        @Override
        public String itemTranslationKey(String id) {
            Item item = Item.getAssetMap().getAsset(id);
            return item == null ? null : item.getTranslationKey();
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

    /**
     * {@return the root interaction an item runs on a slot, or null} Every
     * item is completed with the unarmed defaults at load, so a slot the
     * item does not write is the same as {@link #unarmedRootInteraction}.
     */
    @Nullable
    String itemRootInteraction(String itemId, InteractionType slot);

    /** {@return the root interaction of the unarmed defaults ({@code "Empty"}) on a slot, or null} */
    @Nullable
    String unarmedRootInteraction(InteractionType slot);

    // ---- for the descriptions; nothing below decides what a talent does ----

    /** {@return the {@code "Name"} of an {@code EntityEffect}, null when absent (as in every vanilla file)} */
    @Nullable
    default String entityEffectName(String id) {
        return null;
    }

    /** {@return whether an {@code EntityEffect} declares {@code "Debuff": true}} */
    default boolean isDebuff(String id) {
        return false;
    }

    /** {@return whether an {@code EntityEffect} only applies under {@code "ApplyConditions"}} */
    default boolean hasApplyConditions(String id) {
        return false;
    }

    /** {@return the cooldown of a {@code RootInteraction} in seconds, 0 when it has none} */
    default double rootCooldown(String id) {
        return 0;
    }

    /** {@return the translation key of an item's name, null for an unknown item} */
    @Nullable
    default String itemTranslationKey(String id) {
        return null;
    }
}
