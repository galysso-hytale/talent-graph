package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.RemovalBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.talentgraph.api.TalentId;
import dev.galysso.talentgraph.asset.GraphEffects;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Reconciles the entity effects a player carries with the
 * {@link EntityEffectLink}s of the talents they rank.
 *
 * <p>An effect is held as long as its talent is, as an <em>infinite</em>
 * effect whatever the asset says about duration, and removed completely
 * when the talent goes, whatever the asset's removal behaviour. Vanilla
 * clears every effect at death and puts them back at nothing: the applied
 * state is therefore read from the effect controller itself, and what the
 * {@link AppliedEffectsComponent} adds is memory: which of the effects the
 * player carries are ours, so one whose talent vanished from the graphs
 * can be taken back.</p>
 *
 * <p>An effect the server no longer knows (its file was removed) cannot be
 * removed either; the controller drops it on its own at the next load, and
 * the record forgets it here.</p>
 */
final class EntityEffectSync {

    private EntityEffectSync() {
    }

    /**
     * Brings the effects placed by talents in line with the ranked talents.
     *
     * @param ref        the player entity, on its world thread
     * @param accessor   the store or command buffer of that thread
     * @param controller the player's effect controller
     * @param applied    the record of what was placed, updated in place
     * @param rankOf     the rank reached in a talent, 0 if none
     * @param catalog    the effects of the loaded graphs
     * @return how many effects were placed or removed
     */
    static int sync(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor,
                    EffectControllerComponent controller, AppliedEffectsComponent applied,
                    ToIntFunction<TalentId> rankOf, EffectCatalog catalog) {
        Set<String> desired = desired(rankOf, catalog);
        Set<String> placed = new LinkedHashSet<>();
        int changes = 0;
        for (String id : applied.entityEffects()) {
            if (desired.contains(id)) {
                continue;
            }
            int index = EntityEffect.getAssetMap().getIndex(id);
            if (index != Integer.MIN_VALUE && controller.hasEffect(index)) {
                controller.removeEffect(ref, index, RemovalBehavior.COMPLETE, accessor);
                changes++;
            }
        }
        for (String id : desired) {
            int index = EntityEffect.getAssetMap().getIndex(id);
            if (index == Integer.MIN_VALUE) {
                // Validated at load, gone since: nothing to place until it is back.
                continue;
            }
            if (controller.hasEffect(index)) {
                placed.add(id);
                continue;
            }
            EntityEffect effect = EntityEffect.getAssetMap().getAsset(index);
            // False when the asset's ApplyConditions refuse: tried again next sync.
            if (effect != null && controller.addInfiniteEffect(ref, index, effect, accessor)) {
                placed.add(id);
                changes++;
            }
        }
        applied.setEntityEffects(placed);
        return changes;
    }

    /** The effect ids the ranked talents hold, in graph and talent order. */
    static Set<String> desired(ToIntFunction<TalentId> rankOf, EffectCatalog catalog) {
        Set<String> ids = new LinkedHashSet<>();
        for (GraphEffects graph : catalog.all().values()) {
            for (Map.Entry<TalentId, List<TalentEffect>> talent : graph.byTalent().entrySet()) {
                int rank = rankOf.applyAsInt(talent.getKey());
                if (rank <= 0) {
                    continue;
                }
                for (TalentEffect effect : talent.getValue()) {
                    if (effect instanceof EntityEffectLink link && link.appliesAt(rank)) {
                        ids.add(link.idAt(rank));
                    }
                }
            }
        }
        return ids;
    }
}
