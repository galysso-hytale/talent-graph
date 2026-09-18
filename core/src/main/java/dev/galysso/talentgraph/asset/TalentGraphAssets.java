package dev.galysso.talentgraph.asset;

import com.hypixel.hytale.assetstore.AssetExtraInfo;
import com.hypixel.hytale.assetstore.AssetMap;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.event.RemovedAssetsEvent;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.codec.exception.CodecException;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.LoadAssetEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import dev.galysso.talentgraph.api.TalentId;
import dev.galysso.talentgraph.effect.EffectCatalog;
import dev.galysso.talentgraph.effect.EffectEngine;
import dev.galysso.talentgraph.effect.References;
import dev.galysso.talentgraph.internal.LiveReload;
import dev.galysso.talentgraph.internal.TalentGraphApiImpl;
import dev.galysso.talentgraph.ui.GraphLayouts;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

/**
 * Bridges the server's asset store and the talent registry: every
 * {@code Server/TalentGraph/Graphs/*.json} goes through {@link GraphLoader}
 * at boot and on hot reload, is registered when it has no error, and is
 * handed to {@link LiveReload} either way so the admin tracking the file
 * sees the result.
 *
 * <p>Asset keys follow Hytale's {@code Capitalized_Name} convention while
 * {@link TalentId} is lowercase, so {@code Dark_Knight.json} becomes the graph
 * {@code talentgraph:dark_knight}.</p>
 *
 * <p>A file the store cannot parse is dropped by the store, which then
 * reports it as removed. Since the file is still there, that removal is
 * turned into a "cannot be read" report and the previous graph stays.</p>
 *
 * <p>At boot the store hands over its files while other packs may still be
 * loading, so the stats, effects, interactions and items an effect names
 * are not all known yet. Those loads are held back and run once
 * {@link LoadAssetEvent} reaches its late priority, after every pack. A
 * hot reload happens with everything loaded and is validated at once.</p>
 */
public final class TalentGraphAssets {

    private static final String ASSET_PATH = "TalentGraph/Graphs";

    private final HytaleLogger logger;
    private final TalentGraphApiImpl api;
    private final GraphLayouts layouts;
    private final EffectCatalog effects;
    private final EffectEngine engine;
    private final LiveReload live;
    /** Whether every asset pack is loaded, so effect references can be checked. */
    private boolean assetsReady;
    /** Files handed over before {@link #assetsReady}, in arrival order; guarded by {@code this}. */
    private final Map<String, TalentGraphAsset> deferred = new LinkedHashMap<>();
    /** Graph id of each asset key, registered or not, so removals need no namespace lookup. */
    private final ConcurrentMap<String, TalentId> known = new ConcurrentHashMap<>();
    /** Source file of each asset key, to tell a parse failure from a deletion. */
    private final ConcurrentMap<String, Path> files = new ConcurrentHashMap<>();

    public TalentGraphAssets(HytaleLogger logger, TalentGraphApiImpl api, GraphLayouts layouts,
                             EffectCatalog effects, EffectEngine engine, LiveReload live) {
        this.logger = logger;
        this.api = api;
        this.layouts = layouts;
        this.effects = effects;
        this.engine = engine;
        this.live = live;
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
        plugin.getEventRegistry().register(LoadAssetEvent.PRIORITY_LOAD_LATE, LoadAssetEvent.class,
                event -> onAllAssetsLoaded());
    }

    /**
     * Reads every known graph file again through the asset store, as the
     * file watcher would on a change. Files that fail to parse come back as
     * removals, handled like any other.
     *
     * @return how many files were submitted
     */
    public int reload() {
        AssetStore<String, TalentGraphAsset, DefaultAssetMap<String, TalentGraphAsset>> store =
                AssetRegistry.getAssetStore(TalentGraphAsset.class);
        Map<String, List<Path>> byPack = new HashMap<>();
        for (Path path : files.values()) {
            for (AssetPack pack : AssetModule.get().getAssetPacks()) {
                if (pack.contains(path)) {
                    byPack.computeIfAbsent(pack.getName(), k -> new ArrayList<>()).add(path);
                    break;
                }
            }
        }
        int count = 0;
        for (Map.Entry<String, List<Path>> e : byPack.entrySet()) {
            store.loadAssetsFromPaths(e.getKey(), e.getValue());
            count += e.getValue().size();
        }
        return count;
    }

    private void onLoaded(LoadedAssetsEvent<String, TalentGraphAsset,
            AssetMap<String, TalentGraphAsset>> event) {
        for (TalentGraphAsset asset : event.getLoadedAssets().values()) {
            Path path = event.getAssetMap().getPath(asset.getId());
            if (path != null) {
                files.put(asset.getId(), path);
            }
            synchronized (this) {
                if (!assetsReady) {
                    deferred.put(asset.getId(), asset);
                    continue;
                }
            }
            loadSafely(asset, path);
        }
    }

    /** Every pack is loaded: the files held back at boot can be checked. */
    private void onAllAssetsLoaded() {
        List<TalentGraphAsset> pending;
        synchronized (this) {
            assetsReady = true;
            pending = new ArrayList<>(deferred.values());
            deferred.clear();
        }
        for (TalentGraphAsset asset : pending) {
            loadSafely(asset, files.get(asset.getId()));
        }
    }

    /** One broken file must not prevent the others from loading. */
    private void loadSafely(TalentGraphAsset asset, @Nullable Path path) {
        try {
            load(asset, path);
        } catch (RuntimeException e) {
            logger.at(Level.SEVERE).withCause(e)
                    .log("Talent graph '%s' could not be loaded: %s", asset.getId(), e.getMessage());
        }
    }

    private void onRemoved(RemovedAssetsEvent<String, TalentGraphAsset,
            AssetMap<String, TalentGraphAsset>> event) {
        for (String key : event.getRemovedAssets()) {
            synchronized (this) {
                deferred.remove(key);
            }
            Path path = files.get(key);
            TalentId graphId = known.get(key);
            if (path != null && Files.exists(path)) {
                // Not deleted, just unreadable: the store gave up on it.
                String message = describe(path, key);
                logger.at(Level.SEVERE).log("Talent graph '%s' cannot be read: %s", key, message);
                if (graphId != null) {
                    live.onUnreadable(graphId, path.getFileName().toString(), message);
                }
                continue;
            }
            files.remove(key);
            known.remove(key);
            if (graphId != null) {
                api.removeGraph(graphId);
                layouts.remove(graphId);
                effects.remove(graphId);
                engine.syncAll();
                live.onRemoved(graphId);
                logger.at(Level.INFO).log("Talent graph %s removed", graphId);
            }
        }
    }

    private void load(TalentGraphAsset asset, @Nullable Path path) {
        String fileName = path != null ? path.getFileName().toString() : asset.getId() + ".json";
        LoadedGraph result = GraphLoader.load(asset, fileName, References.LIVE);
        TalentId graphId = result.graph().id();
        TalentId previous = known.put(asset.getId(), graphId);
        if (previous != null && !previous.equals(graphId)) {
            // The Namespace field changed on reload: the old id must go away.
            api.removeGraph(previous);
            layouts.remove(previous);
            effects.remove(previous);
            live.onRemoved(previous);
        }
        GraphReport report = result.report();
        for (GraphProblem problem : report.problems()) {
            logger.at(problem.isError() ? Level.SEVERE : Level.WARNING).log("%s: %s%s", fileName,
                    problem.talent() != null ? "talent '" + problem.talent().path() + "': " : "",
                    problem.message());
        }
        boolean registered = !report.hasErrors();
        if (registered) {
            api.replaceGraph(result.graph());
            layouts.put(graphId, result.layout());
            effects.put(graphId, result.effects());
            // Registry and catalog both updated: online players get the new amounts.
            engine.syncAll();
            logger.at(Level.INFO).log("Talent graph %s loaded (%d talents%s)", graphId,
                    result.graph().talents().size(),
                    report.isEmpty() ? "" : ", " + report.summary());
        } else {
            logger.at(Level.SEVERE).log("Talent graph %s rejected: %s; %s", graphId, report.summary(),
                    api.registry().graph(graphId).isPresent() ? "the previous version stays" : "not registered");
        }
        live.onLoaded(result, registered);
    }

    /**
     * Parses a file the store rejected, to say where it breaks: the store
     * logs that itself but keeps it out of reach of the event.
     */
    private static String describe(Path path, String key) {
        try (Reader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            RawJsonReader reader = new RawJsonReader(in, new char[65536]);
            try {
                reader.consumeWhiteSpace();
                TalentGraphAsset.CODEC.decodeJsonAsset(reader, new AssetExtraInfo<>(path,
                        new AssetExtraInfo.Data(TalentGraphAsset.class, key, null)));
                return "rejected by the asset store, see the server log";
            } catch (CodecException e) {
                String message = e.getRawMessage();
                if (e.getCause() != null && e.getCause().getMessage() != null) {
                    message += " (" + e.getCause().getMessage() + ")";
                }
                return at(path, reader) + message;
            } catch (IOException | RuntimeException e) {
                return at(path, reader) + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        } catch (IOException e) {
            return path.getFileName() + ": " + e.getMessage();
        }
    }

    private static String at(Path path, RawJsonReader reader) {
        return path.getFileName() + ":" + reader.getLine() + ":" + reader.getColumn() + ": ";
    }
}
