package dev.galysso.talentgraph.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.talentgraph.api.TalentGraph;
import dev.galysso.talentgraph.api.TalentGraphApi;
import dev.galysso.talentgraph.api.TalentId;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * {@code /talents reset [graph]}: clears the sender's ranks in a graph and
 * refunds the points spent there. Without argument, resets the graph
 * {@code /talents} displays.
 */
final class ResetSubCommand extends AbstractPlayerCommand {

    private final TalentGraphApi api;
    private final OptionalArg<String> graphArg =
            withOptionalArg("graph", "Graph id, e.g. warrior or talentgraph:warrior", ArgTypes.STRING);

    ResetSubCommand(TalentGraphApi api) {
        super("reset", "Reset your talents in a graph and refund the points");
        this.api = api;
        setPermissionGroups("hytale:WorldEditor");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        Optional<TalentGraph> graph = graphArg.provided(context)
                ? findGraph(graphArg.get(context))
                : TalentsCommand.defaultGraph(api);
        if (graph.isEmpty()) {
            context.sendMessage(Message.raw(graphArg.provided(context)
                    ? "Unknown talent graph: " + graphArg.get(context)
                    : "No talent graph registered."));
            return;
        }
        TalentId graphId = graph.get().id();
        int refunded = api.talentsOf(playerRef.getUuid()).reset(graphId);
        context.sendMessage(Message.raw("Reset " + graphId + ", " + refunded + " point(s) refunded"));
    }

    /** Accepts {@code ns:path} or a bare path matched against every registered graph. */
    private Optional<TalentGraph> findGraph(String reference) {
        if (reference.indexOf(':') >= 0) {
            try {
                return api.registry().graph(TalentId.parse(reference));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
        return api.registry().graphs().stream()
                .filter(g -> g.id().path().equals(reference))
                .findFirst();
    }
}
