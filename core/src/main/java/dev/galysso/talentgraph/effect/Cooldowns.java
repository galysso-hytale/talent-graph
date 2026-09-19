package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.logging.Level;

/**
 * Reads how far a player's cooldowns are, for the ability HUD.
 *
 * <p>Vanilla keeps them in {@code InteractionManager.cooldownHandler}, a
 * map from cooldown id to {@link CooldownHandler.Cooldown} whose remaining
 * time, charge count and charge timer have no getter; an entry is dropped
 * the moment it ends. All three are read by reflection, as the costs of
 * the tooltips are. Any failure — a server update renaming a field — reads
 * as "ready", once logged, so the HUD degrades to no sweep rather than
 * breaking.</p>
 */
public final class Cooldowns {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nullable
    private static final Field HANDLER = field(InteractionManager.class, "cooldownHandler");
    @Nullable
    private static final Field REMAINING = field(CooldownHandler.Cooldown.class, "remainingCooldown");
    @Nullable
    private static final Field CHARGE_COUNT = field(CooldownHandler.Cooldown.class, "chargeCount");
    @Nullable
    private static final Field CHARGE_TIMER = field(CooldownHandler.Cooldown.class, "chargeTimer");

    private Cooldowns() {
    }

    /**
     * The fraction of a cooldown still to wait, 0 when the ability is ready.
     *
     * @param manager the player's interaction manager
     * @param id      the cooldown id ({@link References#rootCooldownId})
     */
    public static double fraction(InteractionManager manager, String id) {
        if (HANDLER == null) {
            return 0;
        }
        try {
            return fraction((CooldownHandler) HANDLER.get(manager), id);
        } catch (ReflectiveOperationException | ClassCastException e) {
            fail(e);
            return 0;
        }
    }

    /**
     * The fraction of a cooldown still to wait, 0 when ready: the remaining
     * time over the cooldown; when that has run out but every charge is
     * spent, the time to the next charge over its recharge time.
     */
    public static double fraction(@Nullable CooldownHandler handler, String id) {
        if (handler == null || REMAINING == null || CHARGE_COUNT == null || CHARGE_TIMER == null) {
            return 0;
        }
        CooldownHandler.Cooldown cooldown = handler.getCooldown(id);
        if (cooldown == null) {
            return 0;
        }
        try {
            float remaining = REMAINING.getFloat(cooldown);
            float max = cooldown.getCooldown();
            if (remaining > 0 && max > 0) {
                return Math.min(1, remaining / max);
            }
            float[] charges = cooldown.getCharges();
            if (CHARGE_COUNT.getInt(cooldown) <= 0 && charges.length > 0 && charges[0] > 0) {
                return Math.max(0, Math.min(1, (charges[0] - CHARGE_TIMER.getFloat(cooldown)) / charges[0]));
            }
            return 0;
        } catch (ReflectiveOperationException e) {
            fail(e);
            return 0;
        }
    }

    @Nullable
    private static Field field(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.at(Level.WARNING).log("Cannot read %s.%s (%s); the ability HUD shows no cooldowns",
                    type.getSimpleName(), name, e);
            return null;
        }
    }

    private static boolean failed;

    private static void fail(Exception e) {
        if (!failed) {
            failed = true;
            LOGGER.at(Level.WARNING).log("Cannot read a cooldown (%s); the ability HUD shows no cooldowns", e);
        }
    }
}
