package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.protocol.InteractionCooldown;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.ValueType;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.entitystats.asset.EntityStatType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.UnarmedInteractions;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.StatsConditionBaseInteraction;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        public int statIndex(String id) {
            return EntityStatType.getAssetMap().getIndex(id);
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
        public String rootCooldownId(String id) {
            RootInteraction root = RootInteraction.getAssetMap().getAsset(id);
            InteractionCooldown cooldown = root == null ? null : root.getCooldown();
            return cooldown == null || cooldown.cooldownId == null ? id : cooldown.cooldownId;
        }

        @Override
        public String itemTranslationKey(String id) {
            Item item = Item.getAssetMap().getAsset(id);
            return item == null ? null : item.getTranslationKey();
        }

        @Override
        public List<Cost> rootCosts(String id) {
            RootInteraction root = RootInteraction.getAssetMap().getAsset(id);
            String[] chain = root == null ? null : root.getInteractionIds();
            if (chain == null || chain.length == 0) {
                return List.of();
            }
            Interaction first = Interaction.getAssetMap().getAsset(chain[0]);
            if (!(first instanceof StatsConditionBaseInteraction condition)) {
                return List.of();
            }
            // The config keeps its costs behind protected fields, without getters.
            try {
                Field rawCosts = StatsConditionBaseInteraction.class.getDeclaredField("rawCosts");
                Field valueType = StatsConditionBaseInteraction.class.getDeclaredField("valueType");
                rawCosts.setAccessible(true);
                valueType.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, Float> costs = (Map<String, Float>) rawCosts.get(condition);
                boolean percent = valueType.get(condition) == ValueType.Percent;
                if (costs == null) {
                    return List.of();
                }
                List<Cost> result = new ArrayList<>();
                for (Map.Entry<String, Float> e : costs.entrySet()) {
                    result.add(new Cost(e.getKey(), e.getValue(), percent));
                }
                return result;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return List.of();
            }
        }
    };

    /** {@return whether an {@code EntityStatType} of this id is loaded} */
    boolean hasStat(String id);

    /** {@return the index of a stat in the player's stat map, {@code Integer.MIN_VALUE} if unknown} */
    default int statIndex(String id) {
        return Integer.MIN_VALUE;
    }

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

    /**
     * {@return the key under which the player's cooldown handler tracks a
     * {@code RootInteraction}: its {@code Cooldown.Id} when it names one,
     * else its own id — the key a {@code TriggerCooldown} inside the chain
     * should use to be seen}
     */
    default String rootCooldownId(String id) {
        return id;
    }

    /** {@return the translation key of an item's name, null for an unknown item} */
    @Nullable
    default String itemTranslationKey(String id) {
        return null;
    }

    /**
     * One cost a {@code StatsCondition} checks.
     *
     * @param stat    the stat id
     * @param amount  the amount, absolute or a percentage of the maximum
     * @param percent whether the amount is a percentage
     */
    record Cost(String stat, double amount, boolean percent) {
    }

    /**
     * {@return the costs stated by a {@code StatsCondition} at the top of a
     * root interaction's chain, empty when the chain starts otherwise}
     * Only the first interaction of the root is read: a cost deeper in the
     * chain (behind a {@code Condition}) is the author's to describe.
     */
    default List<Cost> rootCosts(String id) {
        return List.of();
    }
}
