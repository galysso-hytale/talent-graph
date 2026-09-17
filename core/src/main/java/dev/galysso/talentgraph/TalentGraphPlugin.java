package dev.galysso.talentgraph;

import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.galysso.talentgraph.api.internal.TalentGraphApiHolder;
import dev.galysso.talentgraph.asset.TalentGraphAssets;
import dev.galysso.talentgraph.command.TalentsCommand;
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

    public TalentGraphPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        // Published from the constructor, not setup(): dependent plugins may
        // already be resolving the API by the time our own setup() runs.
        TalentGraphApiHolder.install(api);
    }

    @Override
    protected void setup() {
        new TalentGraphAssets(getLogger(), api, layouts).register(this);
        var progressType = getEntityStoreRegistry().registerComponent(
                TalentProgressComponent.class, "TalentGraphProgress", TalentProgressComponent.CODEC);
        getEntityStoreRegistry().registerSystem(new TalentProgressSystem(getLogger(), api, progressType));
        getCommandRegistry().registerCommand(
                new TalentsCommand("talents", "Open the talent page", api, layouts));
        PacketAdapters.registerInbound(TalentGraphPage.EVENT_FILTER);
    }
}
