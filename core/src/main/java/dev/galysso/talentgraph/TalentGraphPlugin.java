package dev.galysso.talentgraph;

import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.galysso.talentgraph.api.internal.TalentGraphApiHolder;
import dev.galysso.talentgraph.asset.TalentGraphAssets;
import dev.galysso.talentgraph.command.TalentsCommand;
import dev.galysso.talentgraph.effect.EffectCatalog;
import dev.galysso.talentgraph.internal.LiveReload;
import dev.galysso.talentgraph.internal.TalentGraphApiImpl;
import dev.galysso.talentgraph.internal.TalentProgressComponent;
import dev.galysso.talentgraph.internal.TalentProgressSystem;
import dev.galysso.talentgraph.ui.GraphLayouts;
import dev.galysso.talentgraph.ui.TalentGraphPage;

import javax.annotation.Nonnull;

/**
 * Entry point declared as {@code Main} in {@code manifest.json}.
 */
public class TalentGraphPlugin extends JavaPlugin {

    private final TalentGraphApiImpl api = new TalentGraphApiImpl();
    private final GraphLayouts layouts = new GraphLayouts();
    private final EffectCatalog effects = new EffectCatalog();
    private final LiveReload live = new LiveReload(api, layouts, effects);

    public TalentGraphPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        // Published from the constructor, not setup(): dependent plugins may
        // already be resolving the API by the time our own setup() runs.
        TalentGraphApiHolder.install(api);
    }

    @Override
    protected void setup() {
        TalentGraphAssets assets = new TalentGraphAssets(getLogger(), api, layouts, effects, live);
        assets.register(this);
        var progressType = getEntityStoreRegistry().registerComponent(
                TalentProgressComponent.class, "TalentGraphProgress", TalentProgressComponent.CODEC);
        getEntityStoreRegistry().registerSystem(new TalentProgressSystem(getLogger(), api, progressType));
        getCommandRegistry().registerCommand(
                new TalentsCommand("talents", "Open the talent page", api, live, assets));
        // The page as an interaction target, the native way to open one from
        // any item, block or NPC: {"Type": "OpenCustomUI", "Page": {"Id": "TalentGraph"}}.
        OpenCustomUIInteraction.registerSimple(this, TalentGraphPage.class, "TalentGraph",
                playerRef -> TalentsCommand.pageFor(api, live, playerRef));
        PacketAdapters.registerInbound(TalentGraphPage.EVENT_FILTER);
    }
}
