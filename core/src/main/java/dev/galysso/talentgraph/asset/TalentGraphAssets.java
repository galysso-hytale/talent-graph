package dev.galysso.talentgraph.asset;

import com.hypixel.hytale.assetstore.AssetMap;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.event.RemovedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import dev.galysso.talentgraph.api.TalentException;
import dev.galysso.talentgraph.api.TalentGraph;
import dev.galysso.talentgraph.api.TalentGraphBuilder;
import dev.galysso.talentgraph.api.TalentId;
import dev.galysso.talentgraph.internal.TalentGraphApiImpl;
import dev.galysso.talentgraph.ui.AutoLayout;
import dev.galysso.talentgraph.ui.GraphLayout;
import dev.galysso.talentgraph.ui.GraphLayouts;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

/**
 * Bridges the server's asset store and the talent registry: every
 * {@code Server/TalentGraph/Graphs/*.json} becomes a registered
 * {@link TalentGraph} plus a {@link GraphLayout}, at boot and on hot reload.
 *
 * <p>Asset keys follow Hytale's {@code Capitalized_Name} convention while
 * {@link TalentId} is lowercase, so {@code Dark_Knight.json} becomes the graph
 * {@code talentgraph:dark_knight}.</p>
 */
public final class TalentGraphAssets {

    private static final String ASSET_PATH = "TalentGraph/Graphs";

    private final HytaleLogger logger;
    private final TalentGraphApiImpl api;
    private final GraphLayouts layouts;
    /** Graph registered for each asset key, so removals need no namespace lookup. */
    private final ConcurrentMap<String, TalentId> loaded = new ConcurrentHashMap<>();

    public TalentGraphAssets(HytaleLogger logger, TalentGraphApiImpl api, GraphLayouts layouts) {
        this.logger = logger;
        this.api = api;
        this.layouts = layouts;
    }

    /**
     * Registers the asset store and the listeners feeding the registry. Call
     * from the plugin's {@code setup()}.
     *
     * @param plugin the owning plugin, for its event registry
     */
    public void register(JavaPlugin plugin) {
        AssetRegistry.register(HytaleAssetStore.builder(TalentGraphAsset.class, new DefaultAssetMap<>())
                .setPath(ASSET_PATH)
                .setCodec(TalentGraphAsset.CODEC)
                .setKeyFunction(TalentGraphAsset::getId)
                .build());
        plugin.getEventRegistry().register(LoadedAssetsEvent.class, TalentGraphAsset.class, this::onLoaded);
        plugin.getEventRegistry().register(RemovedAssetsEvent.class, TalentGraphAsset.class, this::onRemoved);
    }

    private void onLoaded(LoadedAssetsEvent<String, TalentGraphAsset,
            AssetMap<String, TalentGraphAsset>> event) {
        for (TalentGraphAsset asset : event.getLoadedAssets().values()) {
            // One broken file must not prevent the others from loading.
            try {
                load(asset);
            } catch (TalentException | IllegalArgumentException e) {
                logger.at(Level.SEVERE).withCause(e)
                        .log("Talent graph '%s' rejected: %s", asset.getId(), e.getMessage());
            }
        }
    }

    private void onRemoved(RemovedAssetsEvent<String, TalentGraphAsset,
            AssetMap<String, TalentGraphAsset>> event) {
        for (String key : event.getRemovedAssets()) {
            TalentId graphId = loaded.remove(key);
            if (graphId != null) {
                api.removeGraph(graphId);
                layouts.remove(graphId);
                logger.at(Level.INFO).log("Talent graph %s removed", graphId);
            }
        }
    }

    private void load(TalentGraphAsset asset) {
        TalentId graphId = new TalentId(asset.getNamespace(), asset.getId().toLowerCase(Locale.ROOT));
        String prefix = graphId.path() + '/';
        TalentGraphBuilder builder = TalentGraphBuilder.of(graphId, asset.getName());
        Map<TalentId, GraphLayout.Point> positions = new HashMap<>();
        Map<TalentId, String> icons = new HashMap<>();
        boolean positioned = true;
        for (TalentDefinition def : asset.getTalents()) {
            TalentId id = talentId(asset.getNamespace(), prefix, def.getId());
            Set<TalentId> requires = new HashSet<>();
            for (String required : def.getRequires()) {
                requires.add(talentId(asset.getNamespace(), prefix, required));
            }
            int[] cost = validCost(def.getCost(), id);
            builder.talent(id, def.getName(), def.getMaxRank(),
                    rank -> cost[Math.min(rank, cost.length) - 1], requires);
            if (def.hasPosition()) {
                positions.put(id, new GraphLayout.Point(def.getX(), def.getY()));
            } else {
                positioned = false;
            }
            if (def.getIcon() != null) {
                icons.put(id, def.getIcon());
            }
        }
        TalentGraph graph = builder.build();
        TalentId previous = loaded.put(asset.getId(), graphId);
        if (previous != null && !previous.equals(graphId)) {
            // The Namespace field changed on reload: the old id must go away.
            api.removeGraph(previous);
            layouts.remove(previous);
        }
        api.replaceGraph(graph);
        GraphLayout layout;
        if (positioned) {
            layout = GraphLayout.of(positions);
        } else {
            layout = AutoLayout.of(graph);
            if (!positions.isEmpty()) {
                logger.at(Level.WARNING).log(
                        "Talent graph %s: some talents lack X/Y, using automatic layout for all", graphId);
            }
        }
        layouts.put(graphId, layout.withIcons(icons));
        logger.at(Level.INFO).log("Talent graph %s loaded (%d talents)", graphId, graph.talents().size());
    }

    /**
     * Resolves a talent reference from JSON: {@code "cleave"} is local to the
     * graph, {@code "talentgraph:warrior/cleave"} is taken as is.
     */
    private static TalentId talentId(String namespace, String prefix, String reference) {
        return reference.indexOf(':') >= 0
                ? TalentId.parse(reference)
                : new TalentId(namespace, prefix + reference);
    }

    private static int[] validCost(int[] cost, TalentId id) {
        if (cost.length == 0) {
            throw new TalentException("Talent " + id + " has an empty Cost");
        }
        for (int value : cost) {
            if (value < 0) {
                throw new TalentException("Talent " + id + " has a negative cost");
            }
        }
        return cost.clone();
    }
}
