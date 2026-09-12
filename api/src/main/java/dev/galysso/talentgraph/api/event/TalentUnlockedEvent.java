package dev.galysso.talentgraph.api.event;

import dev.galysso.talentgraph.api.TalentId;

import java.util.Objects;
import java.util.UUID;

/**
 * Fired after a player successfully raised a talent by one rank.
 *
 * @param playerId  the player who unlocked the rank
 * @param talentId  the talent that was raised
 * @param newRank   the rank reached, at least {@code 1}
 * @param pointCost the number of points spent
 */
public record TalentUnlockedEvent(UUID playerId, TalentId talentId, int newRank, int pointCost) {

    public TalentUnlockedEvent {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(talentId, "talentId");
    }
}
