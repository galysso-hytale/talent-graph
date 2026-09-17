package dev.galysso.talentgraph.internal;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import dev.galysso.talentgraph.api.TalentGraph;
import dev.galysso.talentgraph.api.TalentId;
import dev.galysso.talentgraph.asset.GraphReport;
import dev.galysso.talentgraph.asset.LoadedGraph;
import dev.galysso.talentgraph.effect.EffectCatalog;
import dev.galysso.talentgraph.ui.GraphLayouts;
import dev.galysso.talentgraph.ui.TalentGraphPage;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What happens on the players' screens when a graph file is reloaded.
 *
 * <p>Detection is the server's own file watcher, which feeds the asset
 * store; this only reacts to its results. Every player looking at a graph
 * that was reloaded without error gets the new version in place, at the
 * same spot and zoom. One admin at a time may also <em>track</em> the files
 * ({@code /talents track start}): that player is told of each reload in the
 * chat and, when the file has problems, sees them drawn on the tree instead
 * of the last good version. The annotated tree exists only for that
 * player; everyone else keeps the registered graph.</p>
 */
public final class LiveReload {

    private static final String COLOR_OK = "#3fa86f";
    private static final String COLOR_BAD = "#e05a5a";
    private static final String COLOR_WARN = "#f2c94c";

    private final TalentGraphApiImpl api;
    private final GraphLayouts layouts;
    private final EffectCatalog effects;
    /** The admin following the files, or null. */
    @Nullable
    private volatile UUID tracker;
    /** Last result of each graph that had something to report, for the tracker's view. */
    private final Map<TalentId, LoadedGraph> annotated = new ConcurrentHashMap<>();

    public LiveReload(TalentGraphApiImpl api, GraphLayouts layouts, EffectCatalog effects) {
        this.api = api;
        this.layouts = layouts;
        this.effects = effects;
    }

    // ---- tracking ----

    /**
     * Makes a player the tracker, replacing the previous one.
     *
     * @return the previous tracker if it was someone else
     */
    public Optional<UUID> startTracking(UUID player) {
        UUID previous = tracker;
        tracker = player;
        return previous == null || previous.equals(player) ? Optional.empty() : Optional.of(previous);
    }

    /** Stops tracking if {@code player} is the tracker; {@return whether it was} */
    public boolean stopTracking(UUID player) {
        if (player.equals(tracker)) {
            tracker = null;
            return true;
        }
        return false;
    }

    public boolean isTracking(UUID player) {
        return player.equals(tracker);
    }

    /** {@return the tracker while online; tracking ends with the session} */
    @Nullable
    private PlayerRef trackerRef() {
        UUID uuid = tracker;
        if (uuid == null) {
            return null;
        }
        PlayerRef ref = Universe.get().getPlayer(uuid);
        if (ref == null) {
            tracker = null;
        }
        return ref;
    }

    // ---- views ----

    /**
     * What a player sees when opening a graph: the tracker gets the last
     * load with its problems drawn, if there were any; anyone else the
     * registered version.
     */
    public LoadedGraph viewFor(UUID player, TalentGraph graph) {
        if (isTracking(player)) {
            LoadedGraph last = annotated.get(graph.id());
            if (last != null) {
                return last;
            }
        }
        return registered(graph);
    }

    /** {@return the registered version of a graph, with nothing to report} */
    private LoadedGraph registered(TalentGraph graph) {
        return LoadedGraph.of(graph, layouts.layoutOf(graph), effects.of(graph.id()));
    }

    // ---- reload results ----

    /**
     * A file was parsed and checked. Called from the asset thread.
     *
     * @param result     the graph and its report
     * @param registered whether the graph replaced the previous version
     */
    public void onLoaded(LoadedGraph result, boolean registered) {
        TalentId graphId = result.graph().id();
        GraphReport report = result.report();
        if (report.isEmpty()) {
            annotated.remove(graphId);
        } else {
            annotated.put(graphId, result);
        }
        PlayerRef admin = trackerRef();
        UUID adminId = admin != null ? admin.getUuid() : null;
        TalentGraphPage.reopen(graphId, page -> {
            if (page.playerId().equals(adminId)) {
                return page.successor(result, true);
            }
            if (!registered) {
                return null; // keeps the last good version
            }
            TalentGraph graph = api.registry().graph(graphId).orElse(null);
            return graph == null ? null : page.successor(registered(graph), false);
        });
        if (admin != null) {
            String name = report.fileName();
            int talents = result.graph().talents().size();
            Message message;
            if (report.isEmpty()) {
                message = Message.raw(name + " reloaded: " + talents + " talent(s)").color(COLOR_OK);
            } else if (registered) {
                message = Message.raw(name + " reloaded with " + report.summary() + ", see the tree").color(COLOR_WARN);
            } else {
                message = Message.raw(name + ": " + report.summary() + ", see the tree; players keep the previous version")
                        .color(COLOR_BAD);
            }
            admin.sendMessage(message);
        }
    }

    /**
     * A file could not be parsed at all. The registered graph, if any, stays
     * and the tracker sees it with the message in a banner.
     *
     * @param graphId  the graph the file used to define
     * @param fileName the file name
     * @param message  where it breaks and why
     */
    public void onUnreadable(TalentId graphId, String fileName, String message) {
        GraphReport report = GraphReport.unparsable(fileName, message);
        TalentGraph graph = api.registry().graph(graphId).orElse(null);
        LoadedGraph shown = graph != null
                ? registered(graph).withReport(report)
                : annotated.containsKey(graphId) ? annotated.get(graphId).withReport(report) : null;
        if (shown != null) {
            annotated.put(graphId, shown);
        }
        PlayerRef admin = trackerRef();
        if (admin == null) {
            return;
        }
        UUID adminId = admin.getUuid();
        if (shown != null) {
            TalentGraphPage.reopen(graphId, page -> page.playerId().equals(adminId) ? page.successor(shown, true) : null);
        }
        admin.sendMessage(Message.raw(message).color(COLOR_BAD));
    }

    /** A graph file was deleted: the pages showing it close. */
    public void onRemoved(TalentId graphId) {
        annotated.remove(graphId);
        TalentGraphPage.closeAll(graphId);
        PlayerRef admin = trackerRef();
        if (admin != null) {
            admin.sendMessage(Message.raw("Talent graph " + graphId + " removed").color(COLOR_WARN));
        }
    }
}
