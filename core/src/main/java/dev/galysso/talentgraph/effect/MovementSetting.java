package dev.galysso.talentgraph.effect;

import com.hypixel.hytale.protocol.MovementSettings;

import javax.annotation.Nullable;
import java.util.function.ObjDoubleConsumer;
import java.util.function.ToDoubleFunction;

/**
 * The closed list of {@code MovementSettings} fields a {@link MovementEffect}
 * may change, each bound to its field so the list cannot drift from the
 * protocol. A field that is not here is not supported; the reference is
 * {@code docs/EFFECTS.md}, which lists the same entries with their meaning.
 *
 * <p>Vanilla values are those of {@code MovementManager.MASTER_DEFAULT}
 * (server 0.6.5), kept here for the documentation and the tooltips.</p>
 */
public enum MovementSetting {
    JUMP_FORCE("JumpForce", 11.8, s -> s.jumpForce, (s, v) -> s.jumpForce = (float) v),
    SWIM_JUMP_FORCE("SwimJumpForce", 10.0, s -> s.swimJumpForce, (s, v) -> s.swimJumpForce = (float) v),
    BASE_SPEED("BaseSpeed", 5.5, s -> s.baseSpeed, (s, v) -> s.baseSpeed = (float) v),
    ACCELERATION("Acceleration", 0.1, s -> s.acceleration, (s, v) -> s.acceleration = (float) v),
    FORWARD_SPRINT_SPEED_MULTIPLIER("ForwardSprintSpeedMultiplier", 1.65,
            s -> s.forwardSprintSpeedMultiplier, (s, v) -> s.forwardSprintSpeedMultiplier = (float) v),
    FORWARD_RUN_SPEED_MULTIPLIER("ForwardRunSpeedMultiplier", 1.0,
            s -> s.forwardRunSpeedMultiplier, (s, v) -> s.forwardRunSpeedMultiplier = (float) v),
    BACKWARD_RUN_SPEED_MULTIPLIER("BackwardRunSpeedMultiplier", 0.65,
            s -> s.backwardRunSpeedMultiplier, (s, v) -> s.backwardRunSpeedMultiplier = (float) v),
    STRAFE_RUN_SPEED_MULTIPLIER("StrafeRunSpeedMultiplier", 0.8,
            s -> s.strafeRunSpeedMultiplier, (s, v) -> s.strafeRunSpeedMultiplier = (float) v),
    FORWARD_WALK_SPEED_MULTIPLIER("ForwardWalkSpeedMultiplier", 0.3,
            s -> s.forwardWalkSpeedMultiplier, (s, v) -> s.forwardWalkSpeedMultiplier = (float) v),
    BACKWARD_WALK_SPEED_MULTIPLIER("BackwardWalkSpeedMultiplier", 0.3,
            s -> s.backwardWalkSpeedMultiplier, (s, v) -> s.backwardWalkSpeedMultiplier = (float) v),
    STRAFE_WALK_SPEED_MULTIPLIER("StrafeWalkSpeedMultiplier", 0.3,
            s -> s.strafeWalkSpeedMultiplier, (s, v) -> s.strafeWalkSpeedMultiplier = (float) v),
    FORWARD_CROUCH_SPEED_MULTIPLIER("ForwardCrouchSpeedMultiplier", 0.55,
            s -> s.forwardCrouchSpeedMultiplier, (s, v) -> s.forwardCrouchSpeedMultiplier = (float) v),
    BACKWARD_CROUCH_SPEED_MULTIPLIER("BackwardCrouchSpeedMultiplier", 0.4,
            s -> s.backwardCrouchSpeedMultiplier, (s, v) -> s.backwardCrouchSpeedMultiplier = (float) v),
    STRAFE_CROUCH_SPEED_MULTIPLIER("StrafeCrouchSpeedMultiplier", 0.45,
            s -> s.strafeCrouchSpeedMultiplier, (s, v) -> s.strafeCrouchSpeedMultiplier = (float) v),
    AIR_SPEED_MULTIPLIER("AirSpeedMultiplier", 1.0, s -> s.airSpeedMultiplier, (s, v) -> s.airSpeedMultiplier = (float) v),
    AIR_CONTROL_MAX_MULTIPLIER("AirControlMaxMultiplier", 3.13,
            s -> s.airControlMaxMultiplier, (s, v) -> s.airControlMaxMultiplier = (float) v),
    CLIMB_SPEED("ClimbSpeed", 0.035, s -> s.climbSpeed, (s, v) -> s.climbSpeed = (float) v),
    CLIMB_SPEED_LATERAL("ClimbSpeedLateral", 0.035, s -> s.climbSpeedLateral, (s, v) -> s.climbSpeedLateral = (float) v),
    CLIMB_UP_SPRINT_SPEED("ClimbUpSprintSpeed", 0.045, s -> s.climbUpSprintSpeed, (s, v) -> s.climbUpSprintSpeed = (float) v),
    CLIMB_DOWN_SPRINT_SPEED("ClimbDownSprintSpeed", 0.055,
            s -> s.climbDownSprintSpeed, (s, v) -> s.climbDownSprintSpeed = (float) v),
    ROLL_TIME_TO_COMPLETE("RollTimeToComplete", 0.9, s -> s.rollTimeToComplete, (s, v) -> s.rollTimeToComplete = (float) v);

    private final String id;
    private final double vanilla;
    private final ToDoubleFunction<MovementSettings> getter;
    private final ObjDoubleConsumer<MovementSettings> setter;

    MovementSetting(String id, double vanilla, ToDoubleFunction<MovementSettings> getter,
                    ObjDoubleConsumer<MovementSettings> setter) {
        this.id = id;
        this.vanilla = vanilla;
        this.getter = getter;
        this.setter = setter;
    }

    /** {@return the spelling used in graph files} */
    public String id() {
        return id;
    }

    /** {@return the value of a vanilla player, for the documentation} */
    public double vanilla() {
        return vanilla;
    }

    public double get(MovementSettings settings) {
        return getter.applyAsDouble(settings);
    }

    public void set(MovementSettings settings, double value) {
        setter.accept(settings, value);
    }

    /** {@return the setting written this way, ignoring case, or null} */
    @Nullable
    public static MovementSetting parse(@Nullable String text) {
        for (MovementSetting s : values()) {
            if (s.id.equalsIgnoreCase(text)) {
                return s;
            }
        }
        return null;
    }
}
