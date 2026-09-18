package dev.galysso.talentgraph.internal;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.galysso.talentgraph.api.PlayerTalents;
import dev.galysso.talentgraph.api.TalentGraph;
import dev.galysso.talentgraph.api.TalentGraphApi;
import dev.galysso.talentgraph.api.TalentId;
import dev.galysso.talentgraph.api.TalentListener;
import dev.galysso.talentgraph.api.TalentRegistry;
import dev.galysso.talentgraph.api.event.TalentUnlockedEvent;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * The single implementation of {@link TalentGraphApi}, published to third-party
 * plugins through {@code TalentGraphApiHolder}.
 */
public final class TalentGraphApiImpl implements TalentGraphApi {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private final TalentRegistryImpl registry = new TalentRegistryImpl();
    private final Map<UUID, PlayerTalentsImpl> players = new ConcurrentHashMap<>();
    private final List<TalentListener> listeners = new CopyOnWriteArrayList<>();
    // Set once by the plugin, after the effect engine exists; a no-op until then.
    private volatile Consumer<UUID> ranksChangedSink = playerId -> { };

    @Override
    public TalentRegistry registry() {
        return registry;
    }

    @Override
    public PlayerTalents talentsOf(UUID playerId) {
        return progressionOf(playerId);
    }

    /**
     * Typed variant of {@link #talentsOf(UUID)} for the persistence layer.
     */
    PlayerTalentsImpl progressionOf(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return players.computeIfAbsent(playerId,
                id -> new PlayerTalentsImpl(id, registry, this::fireTalentUnlocked, this::fireRanksChanged));
    }

    /**
     * Sets what happens after a player's ranks were mutated by an unlock or
     * a reset, once the mutation is visible: the effect engine syncs them.
     *
     * @param sink called with the player identifier, outside any lock
     */
    public void onRanksChanged(Consumer<UUID> sink) {
        this.ranksChangedSink = Objects.requireNonNull(sink, "sink");
    }

    @Override
    public void addListener(TalentListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    @Override
    public boolean removeListener(TalentListener listener) {
        return listeners.remove(listener);
    }

    /**
     * Registers a graph loaded from data, replacing a previous version with
     * the same id on hot reload. Online players keep their ranks by id and
     * are refunded what the new version no longer offers; offline players
     * keep their entries as saved, a talent that comes back finds them.
     *
     * @param graph the graph to add
     * @throws dev.galysso.talentgraph.api.TalentException if one of its
     *         talents already belongs to another graph
     */
    public void replaceGraph(TalentGraph graph) {
        TalentGraph previous = registry.graph(graph.id()).orElse(null);
        registry.replace(graph);
        if (previous != null) {
            for (PlayerTalentsImpl player : players.values()) {
                int refunded = player.reconcile(previous, graph);
                if (refunded > 0) {
                    LOGGER.at(Level.INFO).log("Refunded %d point(s) to %s after reloading %s",
                            refunded, player.playerId(), graph.id());
                }
            }
        }
    }

    /**
     * Drops a graph whose data file was removed.
     *
     * @param graphId the graph identifier
     */
    public void removeGraph(TalentId graphId) {
        registry.remove(graphId);
    }

    /**
     * Drops a player's cached progression, on disconnect.
     *
     * @param playerId the player identifier
     */
    public void forget(UUID playerId) {
        players.remove(playerId);
    }

    private void fireRanksChanged(UUID playerId) {
        ranksChangedSink.accept(playerId);
    }

    private void fireTalentUnlocked(TalentUnlockedEvent event) {
        for (TalentListener listener : listeners) {
            // One misbehaving add-on must not abort the unlock for everyone else.
            try {
                listener.onTalentUnlocked(event);
            } catch (RuntimeException e) {
                LOGGER.at(Level.WARNING).withCause(e).log("Listener %s failed on %s",
                        listener.getClass().getName(), event);
            }
        }
    }
}
