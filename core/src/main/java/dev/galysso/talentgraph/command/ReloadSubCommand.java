package dev.galysso.talentgraph.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import dev.galysso.talentgraph.asset.TalentGraphAssets;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /talents reload}: reads the graph files again, as the file watcher
 * does on its own when it runs. Results go to the log, and to the tracker.
 */
final class ReloadSubCommand extends AbstractCommand {

    private final TalentGraphAssets assets;

    ReloadSubCommand(TalentGraphAssets assets) {
        super("reload", "Read the talent graph files again");
        this.assets = assets;
        setPermissionGroups("hytale:WorldEditor");
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        int count = assets.reload();
        context.sendMessage(Message.raw(count + " graph file(s) reloaded, see the log"
                + (count == 0 ? "; graphs from jars cannot change" : "")));
        return CompletableFuture.completedFuture(null);
    }
}
