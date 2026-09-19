package dev.galysso.talentgraph.ui;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.galysso.talentgraph.effect.AbilityEffect;
import dev.galysso.talentgraph.effect.AbilityRules;
import dev.galysso.talentgraph.effect.Cooldowns;
import dev.galysso.talentgraph.effect.References;
import dev.galysso.talentgraph.effect.Texts;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The HUD block of the talent abilities a player can use right now: one
 * square per bound slot, the icon of the ability (the effect's
 * {@code "Icon"}, else the talent's) with its key in the corner, in slot
 * order. It sits under the mana bar, away from the game's own list of the
 * held item's inputs; the game's list never shows what a talent binds.
 *
 * <p>Fed by the effect engine after each sync, from the same resolution
 * that writes the keys: a slot is shown when, and only when, its root
 * interaction is bound for what the player holds — a losing candidate or
 * an ability whose item is not in hand does not appear. Syncs are frequent
 * (every inventory event); the client is only written when the list
 * changes. The game drops every custom HUD on a world change
 * ({@code HudManager.resetHud}); the sync at world entry posts it again.</p>
 *
 * <p>An ability the player cannot use right now is greyed: on cooldown,
 * or a stat cost at the top of its chain that they cannot pay (the same
 * test as vanilla's {@code StatsCondition}). A cooling one also carries a
 * darker veil that sinks as the cooldown runs out, as action bars do.
 * Other conditions — game mode, crouching, an effect, anything deeper in
 * the chain — are not read. {@link AbilityCooldownSystem} drives it at
 * 10 Hz; only the squares whose look changed are written.</p>
 */
public final class AbilityHud extends CustomUIHud {

    public static final String KEY = "TalentGraphAbilities";

    private static final String DOCUMENT = "Hud/TalentGraph/Abilities.ui";
    private static final String SLOT_UI = "Hud/TalentGraph/Slot.ui";

    /**
     * Whether the key of an ability slot is the client's own
     * {@code HotkeyLabel}, which would show the key the player really
     * bound, rather than our square with the default key. Off: the game
     * uses that element in its HUD legends, but in a HUD of ours it draws
     * nothing, as it did inside a page.
     */
    static final boolean HOTKEY_LABEL = false;

    /** Height in pixels of the veil when the whole cooldown is left; the icon area of Slot.ui. */
    static final int SWEEP_HEIGHT = 44;
    private static final int SWEEP_INSET = 3;

    /**
     * One square of the block.
     *
     * @param slot       the interaction slot bound
     * @param iconPath   the asset path of the image, null for the missing-icon picture
     * @param cooldownId the key of the ability's cooldown in the player's handler
     * @param needs      the stat costs the chain checks first, resolved
     */
    public record Slot(InteractionType slot, @Nullable String iconPath, String cooldownId, List<Need> needs) {
    }

    /**
     * A stat cost, as {@code StatsCondition} checks it.
     *
     * @param statIndex the stat's index in the player's map, {@code Integer.MIN_VALUE} if unknown
     * @param amount    the value required
     * @param percent   whether the amount is a percentage of the stat's range
     */
    public record Need(int statIndex, float amount, boolean percent) {

        /** Whether the player's stats meet this cost; an absent stat never does, as for vanilla. */
        boolean met(@Nullable EntityStatMap stats) {
            if (stats == null || statIndex == Integer.MIN_VALUE) {
                return false;
            }
            EntityStatValue stat = stats.get(statIndex);
            if (stat == null) {
                return false;
            }
            float value = percent ? stat.asPercentage() * 100f : stat.get();
            return value >= amount;
        }
    }

    private final Texts texts;
    /** What the client shows, to skip the syncs that change nothing. */
    private List<Slot> shown;
    /** The veil height the client shows per square, 0 when the cooldown is over. */
    private int[] sweeps;
    /** Whether each square is greyed on the client. */
    private boolean[] dims;

    private AbilityHud(PlayerRef playerRef, List<Slot> slots) {
        super(playerRef, KEY);
        this.texts = Texts.of(playerRef.getLanguage());
        this.shown = slots;
        this.sweeps = new int[slots.size()];
        this.dims = new boolean[slots.size()];
    }

    /**
     * The squares to show for what the rules bound, in slot order.
     *
     * @param bound the winning ability per slot
     * @param refs  the server's assets, for the cooldown ids
     */
    public static List<Slot> slots(Map<InteractionType, AbilityRules.Bound> bound, References refs) {
        List<Slot> slots = new ArrayList<>(bound.size());
        for (InteractionType slot : AbilityEffect.SLOTS) {
            AbilityRules.Bound b = bound.get(slot);
            if (b != null) {
                String cooldownId = b.effect().cooldownId();
                if (cooldownId == null) {
                    cooldownId = refs.rootCooldownId(b.rootId());
                }
                List<Need> needs = new ArrayList<>();
                for (References.Cost cost : refs.rootCosts(b.rootId())) {
                    needs.add(new Need(refs.statIndex(cost.stat()), (float) cost.amount(), cost.percent()));
                }
                slots.add(new Slot(slot, b.iconPath(), cooldownId, List.copyOf(needs)));
            }
        }
        return List.copyOf(slots);
    }

    /**
     * Posts the block to a player, or brings it in line with the slots.
     *
     * @param player    the player component, owner of the HUDs
     * @param playerRef the player
     * @param slots     the squares to show, empty to hide the block
     */
    public static void sync(Player player, PlayerRef playerRef, List<Slot> slots) {
        HudManager huds = player.getHudManager();
        if (huds.getCustomHud(KEY) instanceof AbilityHud hud) {
            hud.show(slots);
            return;
        }
        // Absent: never posted, or dropped by a world change. addCustomHud
        // shows it, building the document with the slots.
        huds.addCustomHud(playerRef, new AbilityHud(playerRef, slots));
    }

    private void show(List<Slot> slots) {
        if (slots.equals(shown)) {
            return;
        }
        shown = slots;
        UICommandBuilder commands = new UICommandBuilder();
        fill(commands, slots);
        update(false, commands);
    }

    /**
     * Brings the squares in line with what the player can use: greyed
     * while on cooldown or short of a stat, the veil at the cooldown's
     * height. Called at a fixed rate by {@link AbilityCooldownSystem};
     * writes only the squares whose look changed.
     *
     * @param manager the player's interaction manager, owner of the cooldowns
     * @param stats   the player's stats, null if none
     */
    void tickUsability(InteractionManager manager, @Nullable EntityStatMap stats) {
        UICommandBuilder commands = null;
        for (int i = 0; i < shown.size(); i++) {
            Slot slot = shown.get(i);
            int height = (int) Math.ceil(Cooldowns.fraction(manager, slot.cooldownId()) * SWEEP_HEIGHT);
            boolean dim = height > 0;
            for (Need need : slot.needs()) {
                if (!need.met(stats)) {
                    dim = true;
                    break;
                }
            }
            if (height == sweeps[i] && dim == dims[i]) {
                continue;
            }
            sweeps[i] = height;
            dims[i] = dim;
            if (commands == null) {
                commands = new UICommandBuilder();
            }
            look(commands, "#Slots[" + i + "]", dim, height);
        }
        if (commands != null) {
            update(false, commands);
        }
    }

    private static void look(UICommandBuilder commands, String selector, boolean dim, int height) {
        commands.set(selector + " #Dim.Visible", dim);
        commands.set(selector + " #Sweep.Visible", height > 0);
        if (height > 0) {
            Anchor anchor = new Anchor();
            anchor.setLeft(Value.of(SWEEP_INSET));
            anchor.setBottom(Value.of(SWEEP_INSET));
            anchor.setWidth(Value.of(SWEEP_HEIGHT));
            anchor.setHeight(Value.of(height));
            commands.setObject(selector + " #Sweep.Anchor", anchor);
        }
    }

    @Override
    protected void build(UICommandBuilder commands) {
        commands.append(DOCUMENT);
        fill(commands, shown);
    }

    private void fill(UICommandBuilder commands, List<Slot> slots) {
        // Fresh squares look usable; the next tick greys what is not.
        sweeps = new int[slots.size()];
        dims = new boolean[slots.size()];
        commands.set("#Root.Visible", !slots.isEmpty());
        commands.clear("#Slots");
        int index = 0;
        for (Slot slot : slots) {
            commands.append("#Slots", SLOT_UI);
            String selector = "#Slots[" + index++ + "]";
            if (slot.iconPath() != null) {
                commands.set(selector + " #Icon.AssetPath", slot.iconPath());
            }
            String glyph = switch (slot.slot()) {
                case Primary -> "MouseLeft";
                case Secondary -> "MouseRight";
                case Pick -> "MouseMiddle";
                default -> null;
            };
            if (glyph != null) {
                commands.set(selector + " #" + glyph + ".Visible", true);
            } else if (HOTKEY_LABEL) {
                commands.set(selector + " #Hotkey" + slot.slot().name() + ".Visible", true);
            } else {
                commands.set(selector + " #Cap.Visible", true);
                commands.set(selector + " #KeyText.Text", texts.get("talentgraph.key." + slot.slot().name()));
            }
        }
    }
}
