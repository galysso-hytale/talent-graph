package dev.galysso.talentgraph.internal;

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

/**
 * The single implementation of {@link TalentGraphApi}, published to third-party
 * plugins through {@code TalentGraphApiHolder}.
 */
public final class TalentGraphApiImpl implements TalentGraphApi {

    private final TalentRegistryImpl registry = new TalentRegistryImpl();
    private final Map<UUID, PlayerTalentsImpl> players = new ConcurrentHashMap<>();
    private final List<TalentListener> listeners = new CopyOnWriteArrayList<>();

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
                id -> new PlayerTalentsImpl(id, registry, this::fireTalentUnlocked));
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
     * the same id on hot reload.
     *
     * @param graph the graph to add
     * @throws dev.galysso.talentgraph.api.TalentException if one of its
     *         talents already belongs to another graph
     */
    public void replaceGraph(TalentGraph graph) {
        registry.replace(graph);
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

    private void fireTalentUnlocked(TalentUnlockedEvent event) {
        for (TalentListener listener : listeners) {
            // One misbehaving add-on must not abort the unlock for everyone else.
            try {
                listener.onTalentUnlocked(event);
            } catch (RuntimeException e) {
                System.err.println("[TalentGraph] Listener " + listener.getClass().getName()
                        + " failed on " + event + ": " + e);
            }
        }
    }
}
