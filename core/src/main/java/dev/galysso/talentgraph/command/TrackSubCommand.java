package dev.galysso.talentgraph.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.talentgraph.internal.LiveReload;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /talents track start|stop}: follows the graph files while editing
 * them. The sender is told of each reload and sees the problems of a file
 * drawn on the tree; see {@link LiveReload}. One player at a time.
 */
final class TrackSubCommand extends AbstractPlayerCommand {

    private final LiveReload live;
    private final RequiredArg<String> mode = withRequiredArg("mode", "start or stop", ArgTypes.STRING);

    TrackSubCommand(LiveReload live) {
        super("track", "Follow the graph files as you edit them");
        this.live = live;
        setPermissionGroups("hytale:WorldEditor");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        UUID player = playerRef.getUuid();
        switch (mode.get(context).toLowerCase(Locale.ROOT)) {
            case "start" -> {
                boolean replaced = live.startTracking(player).isPresent();
                context.sendMessage(Message.raw("Tracking the graph files: save one and the tree follows"
                        + (replaced ? " (took over from the previous tracker)" : "")));
            }
            case "stop" -> context.sendMessage(Message.raw(live.stopTracking(player)
                    ? "Tracking stopped" : "You were not tracking"));
            default -> context.sendMessage(Message.raw("Usage: /talents track start|stop"));
        }
    }
}
