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
import dev.galysso.talentgraph.ui.GraphLayouts;
import dev.galysso.talentgraph.ui.TalentGraphPage;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.Optional;

/**
 * {@code /talents} opens the talent page on the first registered graph.
 * Sub-commands: {@code list}, {@code grant}, {@code reset}.
 */
public class TalentsCommand extends AbstractPlayerCommand {

    private final TalentGraphApi api;
    private final GraphLayouts layouts;

    public TalentsCommand(String name, String description, TalentGraphApi api, GraphLayouts layouts) {
        super(name, description);
        this.api = api;
        this.layouts = layouts;
        addSubCommand(new ListSubCommand(api));
        addSubCommand(new GrantSubCommand(api));
        addSubCommand(new ResetSubCommand(api));
    }

    /**
     * {@return the graph shown by default: the first one by id}
     */
    static Optional<TalentGraph> defaultGraph(TalentGraphApi api) {
        return api.registry().graphs().stream().min(Comparator.comparing(g -> g.id().toString()));
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        TalentGraph graph = defaultGraph(api).orElse(null);
        if (graph == null) {
            context.sendMessage(Message.raw("No talent graph registered."));
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new TalentGraphPage(
                playerRef, graph, layouts.layoutOf(graph), api.talentsOf(playerRef.getUuid())));
    }
}
