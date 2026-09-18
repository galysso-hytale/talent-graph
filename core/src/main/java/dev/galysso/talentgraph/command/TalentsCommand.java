package dev.galysso.talentgraph.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.talentgraph.api.TalentGraph;
import dev.galysso.talentgraph.api.TalentGraphApi;
import dev.galysso.talentgraph.asset.TalentGraphAssets;
import dev.galysso.talentgraph.internal.LiveReload;
import dev.galysso.talentgraph.ui.TalentGraphPage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * {@code /talents} opens the talent page on the first registered graph.
 * Sub-commands: {@code list}, {@code grant}, {@code reset}, {@code track},
 * {@code reload}.
 */
public class TalentsCommand extends AbstractPlayerCommand {

    private final TalentGraphApi api;
    private final LiveReload live;

    public TalentsCommand(String name, String description, TalentGraphApi api, LiveReload live,
                          TalentGraphAssets assets) {
        super(name, description);
        this.api = api;
        this.live = live;
        addSubCommand(new ListSubCommand(api));
        addSubCommand(new GrantSubCommand(api));
        addSubCommand(new ResetSubCommand(api));
        addSubCommand(new TrackSubCommand(live));
        addSubCommand(new ReloadSubCommand(assets));
    }

    /**
     * {@return the graph shown by default: the first one by id}
     */
    static Optional<TalentGraph> defaultGraph(TalentGraphApi api) {
        return api.registry().graphs().stream().min(Comparator.comparing(g -> g.id().toString()));
    }

    /**
     * The page on the default graph for a player, or null when no graph is
     * registered. Shared by the command and the {@code OpenCustomUI}
     * interaction. The tracker gets the last load with its problems drawn,
     * if any.
     */
    @Nullable
    public static TalentGraphPage pageFor(TalentGraphApi api, LiveReload live, PlayerRef playerRef) {
        TalentGraph graph = defaultGraph(api).orElse(null);
        if (graph == null) {
            return null;
        }
        return pageOn(api, live, playerRef, graph);
    }

    private static TalentGraphPage pageOn(TalentGraphApi api, LiveReload live, PlayerRef playerRef, TalentGraph graph) {
        return new TalentGraphPage(playerRef, live.viewFor(playerRef.getUuid(), graph),
                api.talentsOf(playerRef.getUuid()), null, false, new TalentGraphPage.Navigator() {
            @Override
            public TalentGraphPage neighbour(PlayerRef playerRef, TalentGraph from, int direction) {
                List<TalentGraph> graphs = sortedGraphs(api);
                int index = -1;
                for (int i = 0; i < graphs.size(); i++) {
                    if (graphs.get(i).id().equals(from.id())) {
                        index = i;
                    }
                }
                if (graphs.size() < 2 || index < 0) {
                    return null;
                }
                TalentGraph next = graphs.get(Math.floorMod(index + direction, graphs.size()));
                return pageOn(api, live, playerRef, next);
            }

            @Override
            public boolean hasOthers() {
                return api.registry().graphs().size() > 1;
            }
        });
    }

    /** {@return the registered graphs by id, the order the header arrows walk} */
    private static List<TalentGraph> sortedGraphs(TalentGraphApi api) {
        List<TalentGraph> graphs = new ArrayList<>(api.registry().graphs());
        graphs.sort(Comparator.comparing(g -> g.id().toString()));
        return graphs;
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        TalentGraphPage page = pageFor(api, live, playerRef);
        if (page == null) {
            context.sendMessage(Message.raw("No talent graph registered."));
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, page);
    }
}
