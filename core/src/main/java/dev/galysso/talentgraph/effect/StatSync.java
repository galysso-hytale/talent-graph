package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import dev.galysso.talentgraph.api.TalentId;
import dev.galysso.talentgraph.asset.GraphEffects;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Reconciles a player's stat modifiers with the {@link StatEffect}s of the
 * talents they rank.
 *
 * <p>Every talent of every graph that moves the same bound of the same stat
 * is folded into one vanilla {@link StaticModifier} under a fixed key,
 * one key per bound and calculation, mirroring vanilla's own
 * {@code Effect_ADDITIVE} / {@code Effect_MULTIPLICATIVE}: additive amounts
 * add up, multiplicative ones multiply (−20 % and −10 % give ×0.72). The
 * final arithmetic stays vanilla's ({@code EntityStatValue.computeModifiers}).</p>
 *
 * <p>The applied state is the component itself: each desired modifier is
 * compared with the one under its key and only a difference is written, so
 * a call that changes nothing sends nothing. Modifiers are persisted with
 * the player, which is why the keys never vary: a stale one left in a save
 * is found and corrected by the next call.</p>
 *
 * <p>When a change raises a stat's maximum, the current value rises by the
 * same amount, so the missing part stays what it was: a talent that gives
 * health gives it, it does not leave the player to regenerate it. When
 * the maximum drops, vanilla clamps the value and nothing more is owed.
 * This happens only when a modifier actually changes, hence once.</p>
 */
final class StatSync {

    /** Written before every key, so an admin can spot ours in a save. */
    static final String KEY_PREFIX = "Talent_";

    private static final Modifier.ModifierTarget[] TARGETS = {Modifier.ModifierTarget.MAX, Modifier.ModifierTarget.MIN};
    private static final StaticModifier.CalculationType[] CALCULATIONS =
            {StaticModifier.CalculationType.ADDITIVE, StaticModifier.CalculationType.MULTIPLICATIVE};
    /** One slot per (target, calculation) pair, in that nesting order. */
    private static final int SLOTS = TARGETS.length * CALCULATIONS.length;
    private static final String[] KEYS = new String[SLOTS];

    static {
        for (int slot = 0; slot < SLOTS; slot++) {
            String bound = TARGETS[slot / CALCULATIONS.length] == Modifier.ModifierTarget.MAX
                    ? StatEffect.Target.MAX.id() : StatEffect.Target.MIN.id();
            KEYS[slot] = CALCULATIONS[slot % CALCULATIONS.length].createKey(KEY_PREFIX + bound);
        }
    }

    private StatSync() {
    }

    /**
     * Brings the modifiers under our keys in line with the ranked talents.
     *
     * @param stats     the player's stats, on the player's world thread
     * @param rankOf    the rank reached in a talent, 0 if none
     * @param catalog   the effects of the loaded graphs
     * @param statIndex the index of a stat id in the player's map, negative if unknown
     * @return how many stats had a modifier written or removed
     */
    static int sync(EntityStatMap stats, ToIntFunction<TalentId> rankOf, EffectCatalog catalog,
                    ToIntFunction<String> statIndex) {
        Map<Integer, double[]> desired = desired(rankOf, catalog, statIndex);
        int changes = 0;
        for (int index = 0; index < stats.size(); index++) {
            EntityStatValue value = stats.get(index);
            if (value == null) {
                continue;
            }
            double[] folded = desired.get(index);
            float previousMax = value.getMax();
            boolean changed = false;
            for (int slot = 0; slot < SLOTS; slot++) {
                StaticModifier wanted = folded == null ? null : modifier(slot, folded[slot]);
                Modifier present = value.getModifier(KEYS[slot]);
                if (wanted == null) {
                    if (present != null) {
                        stats.removeModifier(index, KEYS[slot]);
                        changed = true;
                    }
                } else if (!wanted.equals(present)) {
                    stats.putModifier(index, KEYS[slot], wanted);
                    changed = true;
                }
            }
            if (changed) {
                changes++;
                float gained = value.getMax() - previousMax;
                if (gained > 0) {
                    stats.addStatValue(index, gained);
                }
            }
        }
        return changes;
    }

    /**
     * Folds the stat effects of every ranked talent, by stat index, into
     * one amount per slot. A stat the server no longer knows is skipped.
     */
    static Map<Integer, double[]> desired(ToIntFunction<TalentId> rankOf, EffectCatalog catalog,
                                          ToIntFunction<String> statIndex) {
        Map<Integer, double[]> folded = new HashMap<>();
        for (GraphEffects graph : catalog.all().values()) {
            for (Map.Entry<TalentId, List<TalentEffect>> talent : graph.byTalent().entrySet()) {
                int rank = rankOf.applyAsInt(talent.getKey());
                if (rank <= 0) {
                    continue;
                }
                for (TalentEffect effect : talent.getValue()) {
                    if (!(effect instanceof StatEffect stat) || !stat.appliesAt(rank)) {
                        continue;
                    }
                    int index = statIndex.applyAsInt(stat.stat());
                    if (index < 0) {
                        continue;
                    }
                    double[] amounts = folded.computeIfAbsent(index, i -> identity());
                    int slot = slot(stat.target(), stat.calculation());
                    double amount = stat.amount().at(rank);
                    amounts[slot] = stat.calculation() == Calculation.ADDITIVE
                            ? amounts[slot] + amount
                            : amounts[slot] * amount;
                }
            }
        }
        return folded;
    }

    /** The amounts that change nothing: 0 to add, 1 to multiply by. */
    private static double[] identity() {
        double[] amounts = new double[SLOTS];
        for (int slot = 0; slot < SLOTS; slot++) {
            amounts[slot] = CALCULATIONS[slot % CALCULATIONS.length] == StaticModifier.CalculationType.ADDITIVE ? 0 : 1;
        }
        return amounts;
    }

    /** {@return the modifier for a folded amount, or null when it would change nothing} */
    @Nullable
    static StaticModifier modifier(int slot, double amount) {
        StaticModifier.CalculationType calculation = CALCULATIONS[slot % CALCULATIONS.length];
        double neutral = calculation == StaticModifier.CalculationType.ADDITIVE ? 0 : 1;
        if (amount == neutral) {
            return null;
        }
        return new StaticModifier(TARGETS[slot / CALCULATIONS.length], calculation, (float) amount);
    }

    private static int slot(StatEffect.Target target, Calculation calculation) {
        int targetIndex = target == StatEffect.Target.MAX ? 0 : 1;
        int calculationIndex = calculation == Calculation.ADDITIVE ? 0 : 1;
        return targetIndex * CALCULATIONS.length + calculationIndex;
    }
}
