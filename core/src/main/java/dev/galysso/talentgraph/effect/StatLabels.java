package dev.galysso.talentgraph.effect;

import javax.annotation.Nullable;
import java.util.List;

/**
 * What the descriptions know about the vanilla stats: the order they are
 * listed in, the same in every language, and which way is a gain. The
 * words themselves live in {@code talentgraph.lang}
 * ({@code stat.<Id>}); this table mirrors the stat list of
 * {@code docs/EFFECTS.md} and must follow it.
 *
 * <p>The order is the one of the documentation: the resources a player
 * watches first, then the ones opened by items, then the technical ones.
 * A fixed order lets two players compare a node whatever their language;
 * stats this table does not know (a pack's own) come after, alphabetically
 * in the player's language.</p>
 */
public final class StatLabels {

    /**
     * One known stat.
     *
     * @param id            the {@code EntityStatType} id
     * @param higherMaxWins whether raising {@code Max} is a gain
     * @param higherMinWins whether raising {@code Min} is a gain
     */
    public record Known(String id, boolean higherMaxWins, boolean higherMinWins) {
    }

    private static final List<Known> KNOWN = List.of(
            new Known("Health", true, true),
            // Stamina goes negative when overspent, down to Min: a lower
            // floor is a longer lockout, so raising it is the gain.
            new Known("Stamina", true, true),
            new Known("Mana", true, true),
            new Known("Oxygen", true, true),
            new Known("SignatureEnergy", true, true),
            new Known("MagicCharges", true, true),
            new Known("Immunity", true, true),
            // Negative while waiting, capped by Min: raising Min shortens
            // the longest wait.
            new Known("StaminaRegenDelay", true, true),
            new Known("SignatureCharges", true, true),
            new Known("Ammo", true, true),
            new Known("GlidingActive", true, true),
            new Known("DeployablePreview", true, true));

    private StatLabels() {
    }

    /** {@return the stat, or null when the table does not know it} */
    @Nullable
    public static Known of(String id) {
        for (Known known : KNOWN) {
            if (known.id.equals(id)) {
                return known;
            }
        }
        return null;
    }

    /** {@return the position of a stat in the fixed order, {@link Line#NO_ORDER} for an unknown one} */
    public static int order(String id) {
        for (int i = 0; i < KNOWN.size(); i++) {
            if (KNOWN.get(i).id.equals(id)) {
                return i;
            }
        }
        return Line.NO_ORDER;
    }

    /** {@return whether raising the given bound of a stat is a gain; true for an unknown stat} */
    public static boolean higherWins(String id, StatEffect.Target target) {
        Known known = of(id);
        if (known == null) {
            return true;
        }
        return target == StatEffect.Target.MIN ? known.higherMinWins : known.higherMaxWins;
    }

    /** {@return the known ids, in order, for the documentation and the harness} */
    public static List<String> ids() {
        return KNOWN.stream().map(Known::id).toList();
    }
}
