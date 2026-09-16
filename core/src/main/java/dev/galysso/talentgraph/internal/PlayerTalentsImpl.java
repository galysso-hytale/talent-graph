package dev.galysso.talentgraph.internal;

import dev.galysso.talentgraph.api.PlayerTalents;
import dev.galysso.talentgraph.api.Talent;
import dev.galysso.talentgraph.api.TalentException;
import dev.galysso.talentgraph.api.TalentGraph;
import dev.galysso.talentgraph.api.TalentId;
import dev.galysso.talentgraph.api.event.TalentUnlockedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Live progression state for one player.
 *
 * <p>Persistence goes through {@link TalentProgressComponent}: the component
 * is loaded into this object when the player enters a world and reads back
 * from it while the player is online.</p>
 */
final class PlayerTalentsImpl implements PlayerTalents {

    private final UUID playerId;
    private final TalentRegistryImpl registry;
    private final Consumer<TalentUnlockedEvent> unlockedSink;

    // Guarded by `this`: unlocking reads the ranks, checks affordability and
    // writes back, and that sequence must not interleave across threads.
    private final Map<TalentId, Integer> ranks = new HashMap<>();
    private int availablePoints;

    PlayerTalentsImpl(UUID playerId, TalentRegistryImpl registry,
                      Consumer<TalentUnlockedEvent> unlockedSink) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.registry = registry;
        this.unlockedSink = unlockedSink;
    }

    @Override
    public UUID playerId() {
        return playerId;
    }

    @Override
    public synchronized int rank(TalentId id) {
        return ranks.getOrDefault(id, 0);
    }

    @Override
    public synchronized int availablePoints() {
        return availablePoints;
    }

    @Override
    public synchronized boolean canUnlock(TalentId id) {
        return registry.findTalent(id).filter(this::isAffordable).isPresent();
    }

    @Override
    public int unlock(TalentId id) {
        TalentUnlockedEvent event;
        synchronized (this) {
            Talent talent = registry.findTalent(id)
                    .orElseThrow(() -> new TalentException("Unknown talent: " + id));
            int nextRank = ranks.getOrDefault(id, 0) + 1;
            if (nextRank > talent.maxRank()) {
                throw new TalentException(id + " is already at max rank " + talent.maxRank());
            }
            for (TalentId prerequisite : talent.prerequisites()) {
                if (ranks.getOrDefault(prerequisite, 0) < 1) {
                    throw new TalentException(id + " requires " + prerequisite + " first");
                }
            }
            int cost = talent.costOfRank(nextRank);
            if (cost > availablePoints) {
                throw new TalentException("Rank " + nextRank + " of " + id + " costs " + cost
                        + " points but only " + availablePoints + " are available");
            }
            availablePoints -= cost;
            ranks.put(id, nextRank);
            event = new TalentUnlockedEvent(playerId, id, nextRank, cost);
        }
        // Fired outside the lock so a listener cannot deadlock against us.
        unlockedSink.accept(event);
        return event.newRank();
    }

    @Override
    public synchronized int grantPoints(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive, got " + amount);
        }
        availablePoints += amount;
        return availablePoints;
    }

    @Override
    public synchronized int reset(TalentId graphId) {
        TalentGraph graph = registry.graph(graphId)
                .orElseThrow(() -> new TalentException("Unknown talent graph: " + graphId));
        int refunded = 0;
        for (Talent talent : graph.talents()) {
            Integer rank = ranks.remove(talent.id());
            for (int i = 1; rank != null && i <= rank; i++) {
                refunded += talent.costOfRank(i);
            }
        }
        availablePoints += refunded;
        return refunded;
    }

    private boolean isAffordable(Talent talent) {
        int nextRank = ranks.getOrDefault(talent.id(), 0) + 1;
        if (nextRank > talent.maxRank()) {
            return false;
        }
        for (TalentId prerequisite : talent.prerequisites()) {
            if (ranks.getOrDefault(prerequisite, 0) < 1) {
                return false;
            }
        }
        return talent.costOfRank(nextRank) <= availablePoints;
    }

    /**
     * Replaces the whole state with a persisted snapshot.
     *
     * @param points the unspent points
     * @param ranks  the rank reached in each talent, zero entries ignored
     */
    synchronized void load(int points, Map<TalentId, Integer> ranks) {
        this.ranks.clear();
        ranks.forEach((id, rank) -> {
            if (rank > 0) {
                this.ranks.put(id, rank);
            }
        });
        this.availablePoints = Math.max(0, points);
    }

    /**
     * {@return a copy of the ranks reached, for serialisation}
     */
    synchronized Map<TalentId, Integer> snapshot() {
        return new HashMap<>(ranks);
    }

    /**
     * {@return the graph a talent belongs to, for diagnostics}
     */
    Optional<TalentGraph> graphOf(TalentId talentId) {
        return registry.graphOf(talentId);
    }
}
