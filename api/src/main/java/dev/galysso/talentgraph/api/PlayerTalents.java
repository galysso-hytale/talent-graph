package dev.galysso.talentgraph.api;

import java.util.UUID;

/**
 * A live view of one player's progression.
 *
 * <p>Instances are snapshots of nothing: they read through to the current
 * state, so a value read twice may differ. Mutating methods are the only
 * supported way to change progression, since they fire the corresponding
 * {@link dev.galysso.talentgraph.api.event.TalentUnlockedEvent events}.</p>
 */
public interface PlayerTalents {

    /**
     * {@return the identifier of the player this view belongs to}
     */
    UUID playerId();

    /**
     * Returns the rank the player has reached in a talent.
     *
     * @param id the talent identifier
     * @return the current rank, or {@code 0} if the talent is locked or unknown
     */
    int rank(TalentId id);

    /**
     * {@return the number of unspent talent points}
     */
    int availablePoints();

    /**
     * Tests whether {@link #unlock(TalentId)} would succeed, without changing
     * anything.
     *
     * @param id the talent identifier
     * @return {@code true} if prerequisites are met, the rank cap is not
     *         reached and the player can afford the next rank
     */
    boolean canUnlock(TalentId id);

    /**
     * Spends talent points to raise a talent by one rank.
     *
     * @param id the talent identifier
     * @return the rank reached
     * @throws TalentException if the talent is unknown, capped, unaffordable or
     *                         has unmet prerequisites
     */
    int unlock(TalentId id);

    /**
     * Adds unspent talent points, typically on level up.
     *
     * @param amount the number of points to add, must be positive
     * @return the new {@link #availablePoints()} total
     * @throws IllegalArgumentException if {@code amount} is not positive
     */
    int grantPoints(int amount);

    /**
     * Resets every talent of a graph and refunds the points spent in it.
     *
     * @param graphId the graph to reset
     * @return the number of points refunded
     * @throws TalentException if the graph is unknown
     */
    int reset(TalentId graphId);
}
