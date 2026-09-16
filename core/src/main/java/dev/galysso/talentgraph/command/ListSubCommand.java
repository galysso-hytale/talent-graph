package dev.galysso.talentgraph.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import dev.galysso.talentgraph.api.Talent;
import dev.galysso.talentgraph.api.TalentGraph;
import dev.galysso.talentgraph.api.TalentGraphApi;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /talents list}: prints the registered graphs and their talents.
 */
final class ListSubCommand extends AbstractCommand {

    private final TalentGraphApi api;

    ListSubCommand(TalentGraphApi api) {
        super("list", "List the registered talent graphs");
        this.api = api;
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        var graphs = api.registry().graphs();
        if (graphs.isEmpty()) {
            context.sendMessage(Message.raw("No talent graph registered."));
            return CompletableFuture.completedFuture(null);
        }
        context.sendMessage(Message.raw(graphs.size() + " talent graph(s):"));
        for (TalentGraph graph : graphs) {
            context.sendMessage(Message.raw("  " + graph.displayName() + " (" + graph.id() + ")"));
            for (Talent talent : graph.talents()) {
                context.sendMessage(Message.raw("    - " + talent.displayName()
                        + " [max rank " + talent.maxRank() + "]"));
            }
        }
        return CompletableFuture.completedFuture(null);
    }
}
