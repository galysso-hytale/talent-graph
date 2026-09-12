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
 * Lists the registered talent graphs. Smoke test that the plugin loaded and
 * that the API is reachable.
 */
public class TalentsCommand extends AbstractCommand {

    private final TalentGraphApi api;

    public TalentsCommand(String name, String description, TalentGraphApi api) {
        super(name, description);
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
