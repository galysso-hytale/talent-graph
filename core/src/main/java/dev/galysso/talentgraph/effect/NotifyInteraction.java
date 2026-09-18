package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * {@code "Type": "TalentGraph_Notify"} — an interaction that shows a HUD
 * notification to the player running it, and nothing else.
 *
 * <pre>{@code
 * { "Type": "TalentGraph_Notify", "Message": "Your talents do not let you use this" }
 * }</pre>
 *
 * <p>Vanilla has no such thing: {@code ShowEventTitle} addresses a whole
 * world and {@code SendMessage} to {@code Owner} is a chat line. This is
 * what the refusal chain ({@code TalentGraph_Denied}) ends with; a pack can
 * replace that chain's file and use it, or not, as it likes. Server side
 * only: the client simulates it as an instant no-op.</p>
 */
public final class NotifyInteraction extends SimpleInstantInteraction {

    public static final String TYPE = "TalentGraph_Notify";

    public static final BuilderCodec<NotifyInteraction> CODEC = BuilderCodec.builder(
                    NotifyInteraction.class, NotifyInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("Shows a HUD notification to the player running the interaction.")
            .<String>appendInherited(new KeyedCodec<>("Message", Codec.STRING),
                    (o, v) -> o.message = v, o -> o.message, (o, p) -> o.message = p.message)
            .documentation("The text of the notification.")
            .add()
            .build();

    @Nullable
    private String message;

    public NotifyInteraction() {
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Ref<EntityStore> owner = context.getOwningEntity();
        if (commandBuffer == null || owner == null || !owner.isValid() || message == null || message.isBlank()) {
            return;
        }
        PlayerRef playerRef = commandBuffer.getComponent(owner, PlayerRef.getComponentType());
        if (playerRef != null) {
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.raw(message));
        }
    }

    @Override
    protected void simulateFirstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context,
                                    @Nonnull CooldownHandler cooldownHandler) {
    }

    @Nonnull
    @Override
    public String toString() {
        return "NotifyInteraction{message=" + message + "} " + super.toString();
    }
}
