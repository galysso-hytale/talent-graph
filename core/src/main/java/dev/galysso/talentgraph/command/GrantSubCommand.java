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
import dev.galysso.talentgraph.api.TalentGraphApi;

import javax.annotation.Nonnull;

/**
 * {@code /talents grant <count>}: gives the sender talent points. Admin tool
 * until a gameplay source of points exists.
 */
final class GrantSubCommand extends AbstractPlayerCommand {

    private final TalentGraphApi api;
    private final RequiredArg<Integer> count =
            withRequiredArg("count", "Number of talent points to add", ArgTypes.INTEGER);

    GrantSubCommand(TalentGraphApi api) {
        super("grant", "Grant talent points to yourself");
        this.api = api;
        setPermissionGroups("hytale:WorldEditor");
    }

    @Override
    protected void execute(@Nonnull CommandContext context, @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
        int amount = count.get(context);
        if (amount <= 0) {
            context.sendMessage(Message.raw("count must be positive"));
            return;
        }
        int total = api.talentsOf(playerRef.getUuid()).grantPoints(amount);
        context.sendMessage(Message.raw("Granted " + amount + " talent point(s), " + total + " available"));
    }
}
