package dev.galysso.talentgraph.internal;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.talentgraph.api.TalentId;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * Serialised form of a player's progression, stored on the player entity.
 *
 * <p>The live state lives in {@link PlayerTalentsImpl}, which any thread may
 * mutate. This component is only the persistence carrier: while
 * {@link #bind bound} the codec getters read through to the live object, so
 * every save (periodic or on disconnect) captures the current state;
 * {@link #detach()} copies the state back into the snapshot fields so the
 * holder stays serialisable after the live object is dropped.</p>
 *
 * <p>Ranks are stored by talent id string ({@code ns:path}) so progression in
 * a graph whose data file is temporarily missing survives a round trip.</p>
 */
public final class TalentProgressComponent implements Component<EntityStore> {

    private static final RankEntry[] NO_RANKS = new RankEntry[0];

    public static final BuilderCodec<TalentProgressComponent> CODEC = BuilderCodec
            .builder(TalentProgressComponent.class, TalentProgressComponent::new)
            .append(new KeyedCodec<>("Points", Codec.INTEGER, false),
                    (c, v) -> c.points = v == null ? 0 : v,
                    TalentProgressComponent::currentPoints)
            .add()
            .append(new KeyedCodec<>("Ranks", new ArrayCodec<>(RankEntry.CODEC, RankEntry[]::new), false),
                    (c, v) -> c.ranks = v == null ? NO_RANKS : v,
                    TalentProgressComponent::currentRanks)
            .add()
            .build();

    // Snapshot, authoritative only while `live` is null.
    private int points;
    private RankEntry[] ranks = NO_RANKS;
    // Set and cleared on the world thread; volatile so a clone() or a codec
    // read from another thread never sees a stale binding.
    private volatile PlayerTalentsImpl live;

    /**
     * Routes reads to the live object, after its state was loaded from this
     * snapshot.
     */
    void bind(PlayerTalentsImpl talents) {
        this.live = talents;
    }

    /**
     * Freezes the live state into the snapshot and drops the binding.
     */
    void detach() {
        PlayerTalentsImpl talents = live;
        if (talents == null) {
            return;
        }
        points = talents.availablePoints();
        ranks = toEntries(talents.snapshot());
        live = null;
    }

    int savedPoints() {
        return points;
    }

    RankEntry[] savedRanks() {
        return ranks;
    }

    private int currentPoints() {
        PlayerTalentsImpl talents = live;
        return talents == null ? points : talents.availablePoints();
    }

    private RankEntry[] currentRanks() {
        PlayerTalentsImpl talents = live;
        return talents == null ? ranks : toEntries(talents.snapshot());
    }

    private static RankEntry[] toEntries(Map<TalentId, Integer> ranks) {
        RankEntry[] entries = new RankEntry[ranks.size()];
        int i = 0;
        for (Map.Entry<TalentId, Integer> e : ranks.entrySet()) {
            entries[i++] = new RankEntry(e.getKey().toString(), e.getValue());
        }
        return entries;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        TalentProgressComponent copy = new TalentProgressComponent();
        copy.points = currentPoints();
        copy.ranks = currentRanks().clone();
        return copy;
    }

    /**
     * One {@code {"Talent": "ns:path", "Rank": n}} element of {@code Ranks}.
     */
    public static final class RankEntry {

        public static final BuilderCodec<RankEntry> CODEC = BuilderCodec
                .builder(RankEntry.class, RankEntry::new)
                .append(new KeyedCodec<>("Talent", Codec.STRING), (e, v) -> e.talent = v, e -> e.talent)
                .add()
                .append(new KeyedCodec<>("Rank", Codec.INTEGER), (e, v) -> e.rank = v, e -> e.rank)
                .add()
                .build();

        private String talent;
        private int rank;

        RankEntry() {
        }

        RankEntry(String talent, int rank) {
            this.talent = talent;
            this.rank = rank;
        }

        String talent() {
            return talent;
        }

        int rank() {
            return rank;
        }
    }
}
