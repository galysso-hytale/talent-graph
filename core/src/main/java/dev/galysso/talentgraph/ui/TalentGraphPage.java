package dev.galysso.talentgraph.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPage;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageEvent;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageEventType;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.galysso.talentgraph.api.PlayerTalents;
import dev.galysso.talentgraph.api.Talent;
import dev.galysso.talentgraph.api.TalentException;
import dev.galysso.talentgraph.api.TalentGraph;
import dev.galysso.talentgraph.api.TalentId;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * The in-game view of one talent graph for one player.
 *
 * <p>The page shows a fixed-size window on the canvas (see {@link Camera}).
 * The whole graph is drawn once per zoom level inside {@code #Graph}, a
 * group as large as the scaled canvas; moving the window only re-anchors
 * that group inside the clipping {@code #Canvas}, one property update. The
 * window is moved from the minimap in the corner and rescaled by the zoom
 * buttons of the header; only the latter redraws everything.</p>
 *
 * <p>Moves are animated: a request sets a target and a server-side timer
 * glides the window towards it sixty times a second, straight from the timer
 * thread to the wire, so the coarse steps of the minimap grid read as one
 * continuous motion. The server's page manager
 * drops every event of a page received while one of its updates awaits the
 * client's acknowledgement; at thirty updates a second that would make the
 * page deaf to the cursor half of the time, so the events of open pages are
 * taken from the wire before the page manager sees them (see
 * {@link #EVENT_FILTER}). Everything that touches the camera is
 * synchronised, since the timer runs off the world thread.</p>
 *
 * <p>Inside the content come, in order, one link group per talent that has
 * a prerequisite on this canvas, then one node per talent, so nodes always
 * draw on top of links. Both are addressed by child index, recorded at each
 * redraw. Unlocking refreshes the node, its dependents and the link groups
 * touching them, plus the affordance of every other node since the point
 * balance changed, and the minimap dots.</p>
 *
 * <p>Only a talent the player can unlock right now reacts to the cursor: its
 * {@code #Action} overlay is the sole node element bound to an event.
 * Everything else is inert and explains itself through its tooltip.</p>
 */
public final class TalentGraphPage extends InteractiveCustomUIPage<TalentGraphPage.Event> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String PAGE_UI = "Pages/TalentGraph/TalentGraphPage.ui";
    private static final String NODE_UI = "Pages/TalentGraph/Node.ui";
    // Swap for OrthogonalLinkRenderer if the tiled lines prove too heavy.
    private static final LinkRenderer LINKS = new SpriteLinkRenderer();
    private static final String ACTION_CLOSE = "Close";
    /** Logs the minimap clicks, a debugging aid. */
    private static final boolean LOG_NAVIGATION = false;

    /** Size of the canvas window in screen units; must match {@code #Stage} in the page markup. */
    static final int VIEWPORT_WIDTH = 1582;
    static final int VIEWPORT_HEIGHT = 864;
    /** Largest canvas that fits entirely in the window at the widest zoom. */
    public static final int MAX_CANVAS_WIDTH = (int) (VIEWPORT_WIDTH / Camera.ZOOM_LEVELS[Camera.ZOOM_LEVELS.length - 1]);
    public static final int MAX_CANVAS_HEIGHT = (int) (VIEWPORT_HEIGHT / Camera.ZOOM_LEVELS[Camera.ZOOM_LEVELS.length - 1]);
    /** Badges hold text, which does not scale: they only show above this zoom. */
    private static final double BADGE_MIN_ZOOM = 0.5;

    /** Minimap size in screen units; must match {@code #Minimap} in the page markup. */
    private static final int MINIMAP_WIDTH = 300;
    private static final int MINIMAP_HEIGHT = 180;
    /** Inset of the graph inside the minimap. */
    private static final int MINIMAP_PADDING = 6;
    /** Side of the invisible buttons tiling the minimap, i.e. its click precision. */
    private static final int MINIMAP_CELL = 6;
    private static final int DOT_SIZE = 4;
    /** Period of the glide timer, i.e. of the frames it sends. */
    private static final long GLIDE_FRAME_MILLIS = 16;
    /** Time constant of the ease-out: the remaining distance decays by e every τ. */
    private static final double GLIDE_TAU_NANOS = 80_000_000.0;
    /** Snap distance in screen pixels, which ends the glide (and its stream of updates). */
    private static final double GLIDE_SNAP = 1;

    /** The pages open right now, by player, for {@link #EVENT_FILTER}. */
    private static final Map<UUID, TalentGraphPage> OPEN = new ConcurrentHashMap<>();

    /**
     * Delivers the data events of open pages itself, on the world thread
     * like the server would, and swallows the packet so that the page
     * manager never applies its rule "no event while an update is
     * unacknowledged". That rule guards against events sent against a stale
     * page; here a stale unlock is already refused and a stale target is
     * just a target, whereas a dropped hover is a window that stops
     * following the cursor. Acknowledgements and dismissals go their usual
     * way. To register once with {@code PacketAdapters.registerInbound}.
     */
    public static final PlayerPacketFilter EVENT_FILTER = (playerRef, packet) -> {
        if (!(packet instanceof CustomPageEvent event) || event.type != CustomPageEventType.Data) {
            return false;
        }
        TalentGraphPage page = OPEN.get(playerRef.getUuid());
        Ref<EntityStore> ref = playerRef.getReference();
        if (page == null || ref == null || !ref.isValid()) {
            return false;
        }
        Store<EntityStore> store = ref.getStore();
        store.getExternalData().getWorld().execute(() -> {
            // Still the open page: it may have been closed since the packet came in.
            if (ref.isValid() && OPEN.get(playerRef.getUuid()) == page) {
                page.handleDataEvent(ref, store, event.data);
            }
        });
        return true;
    };

    /** Ranks shown as pips; beyond that the progress falls back to "3/10" text. */
    private static final int MAX_PIPS = 5;
    private static final int PIP_SIZE = 8;
    private static final int PIP_PITCH = 11;
    private static final int PIP_PADDING = 5;
    private static final int BADGE_HEIGHT = 16;
    private static final int RANK_BADGE_WIDTH = 30;
    private static final int ICON_SIZE = 64;
    private static final String COLOR_TITLE = "#f0f4ff";
    private static final String COLOR_MUTED = "#96a9be";
    private static final String COLOR_OK = "#3fa86f";
    private static final String COLOR_BLOCKED = "#e05a5a";

    private final TalentGraph graph;
    private final GraphLayout layout;
    private final PlayerTalents talents;
    /** Talents in a fixed order, the one of the minimap dots. */
    private final List<Talent> order;
    /** Talents that list each talent as a prerequisite. */
    private final Map<TalentId, List<Talent>> dependents = new HashMap<>();
    private final Camera camera;
    /** Whether the graph can overflow the window at all; when not, the minimap never exists. */
    private final boolean pannable;
    /** Canvas units to minimap units. */
    private final double minimapScale;
    /** Where the canvas origin lands on the minimap, so the graph is centred in it. */
    private final double minimapLeft;
    private final double minimapTop;

    /** Child index in {@code #Canvas} of the link group and node of each talent shown right now. */
    private final Map<TalentId, Integer> linkIndex = new HashMap<>();
    private final Map<TalentId, Integer> nodeIndex = new HashMap<>();

    /**
     * Follow mode: the window follows the cursor over the minimap. A right
     * click toggles it, a left click ends it; leaving the minimap merely
     * pauses it, the window stays until the cursor comes back. Holding the
     * button cannot do it: the client captures the pointer on the pressed
     * button and no other cell sees the cursor until it is released.
     */
    private boolean following;
    /** The canvas point the window is gliding to; meaningful while {@link #glide} runs. */
    private double targetX;
    private double targetY;
    /** The page manager of the player, captured on the world thread for the glide timer. */
    private PageManager pages;
    /** The running glide timer, or null when the window is at rest. */
    private ScheduledFuture<?> glide;
    private long lastGlideSendNanos;

    public TalentGraphPage(@Nonnull PlayerRef playerRef, TalentGraph graph, GraphLayout layout,
                           PlayerTalents talents) {
        super(playerRef, CustomPageLifetime.CanDismiss, Event.CODEC);
        this.graph = graph;
        this.layout = layout;
        this.talents = talents;
        this.order = new ArrayList<>(graph.talents());
        order.sort(Comparator.comparing(t -> t.id().toString()));
        for (Talent talent : order) {
            for (TalentId prerequisite : talent.prerequisites()) {
                dependents.computeIfAbsent(prerequisite, k -> new ArrayList<>()).add(talent);
            }
        }
        GraphLayout.Bounds bounds = layout.bounds();
        this.camera = new Camera(VIEWPORT_WIDTH, VIEWPORT_HEIGHT, bounds);
        this.pannable = bounds.width() > VIEWPORT_WIDTH || bounds.height() > VIEWPORT_HEIGHT;
        this.minimapScale = Math.min((double) (MINIMAP_WIDTH - 2 * MINIMAP_PADDING) / bounds.width(),
                (double) (MINIMAP_HEIGHT - 2 * MINIMAP_PADDING) / bounds.height());
        this.minimapLeft = (MINIMAP_WIDTH - bounds.width() * minimapScale) / 2;
        this.minimapTop = (MINIMAP_HEIGHT - bounds.height() * minimapScale) / 2;
        // Open on the root of the graph: the first talent without prerequisite.
        for (Talent talent : order) {
            if (talent.prerequisites().isEmpty()) {
                GraphLayout.Point centre = centerOf(talent);
                camera.centerOn(centre.x(), centre.y());
                break;
            }
        }
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        OPEN.put(playerRef.getUuid(), this);
        pages = store.getComponent(ref, Player.getComponentType()).getPageManager();
        commands.append(PAGE_UI);
        commands.set("#GraphName.Text", graph.displayName());
        updatePoints(commands);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of("Action", ACTION_CLOSE), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ZoomIn",
                EventData.of("Zoom", "in"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ZoomOut",
                EventData.of("Zoom", "out"), false);
        if (pannable) {
            buildMinimap(commands, events);
            updateMinimapWindow(commands);
        }
        updateZoomControls(commands);
        renderCanvas(commands, events);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull Event event) {
        if (ACTION_CLOSE.equals(event.action)) {
            close();
        } else if (event.unlock != null) {
            unlock(event.unlock);
        } else if (event.zoom != null) {
            zoom(event.zoom);
        } else if (event.pan != null) {
            pan(event.pan, false);
        } else if (event.toggle != null) {
            pan(event.toggle, true);
        } else if (event.enter != null) {
            enter(event.enter);
        }
    }

    // ---- navigation ----

    private synchronized void zoom(String direction) {
        boolean changed = "in".equals(direction) ? camera.zoomIn() : camera.zoomOut();
        if (!changed) {
            return;
        }
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        updateZoomControls(commands);
        updateMinimapWindow(commands);
        renderCanvas(commands, events);
        sendUpdate(commands, events, false);
    }

    /** A click on a minimap cell: recentre; a right click also toggles follow mode, a left one ends it. */
    private synchronized void pan(String target, boolean toggle) {
        GraphLayout.Point point = parsePoint(target);
        if (point == null) {
            return;
        }
        following = toggle && !following;
        if (LOG_NAVIGATION) {
            LOGGER.at(Level.INFO).log("minimap: %s at %s (following=%b)", toggle ? "right click" : "click", point, following);
        }
        glideTo(point);
    }

    /** The cursor entered a minimap cell: head there if following. */
    private synchronized void enter(String target) {
        if (!following) {
            return;
        }
        GraphLayout.Point point = parsePoint(target);
        if (point == null) {
            return;
        }
        glideTo(point);
    }

    /** Sets the target of the glide and starts the timer if the window was at rest. */
    private void glideTo(GraphLayout.Point point) {
        if (glide == null && camera.isAt(point.x(), point.y(), GLIDE_SNAP / camera.zoom())) {
            return; // already there, nothing to animate
        }
        targetX = point.x();
        targetY = point.y();
        if (glide == null) {
            // As if a frame had just gone out: the first one covers a normal step.
            lastGlideSendNanos = System.nanoTime() - GLIDE_FRAME_MILLIS * 1_000_000L;
            glide = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(this::tick,
                    0, GLIDE_FRAME_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * One frame of the glide, on the timer thread; stops the timer on
     * arrival. Nothing here needs the world thread: the page manager was
     * captured on it, and counting an update then writing a packet are
     * both thread-safe.
     */
    private synchronized void tick() {
        if (glide == null) {
            return; // dismissed between the last frame and the cancellation
        }
        if (playerRef.getReference() == null) {
            stopGlide();
            return;
        }
        long now = System.nanoTime();
        // The share of the remaining distance to cover depends on the time
        // elapsed, so a late tick does not slow the motion down.
        double fraction = 1 - Math.exp(-(now - lastGlideSendNanos) / GLIDE_TAU_NANOS);
        boolean done = camera.approach(targetX, targetY, fraction, GLIDE_SNAP / camera.zoom());
        UICommandBuilder commands = new UICommandBuilder();
        placeContent(commands);
        updateMinimapWindow(commands);
        // Straight to the wire rather than through sendUpdate, which would
        // wait for the world thread and its once-a-tick flush.
        pages.updateCustomPage(new CustomPage(getClass().getName(), false, false, getLifetime(),
                commands.getCommands(), UIEventBuilder.EMPTY_EVENT_BINDING_ARRAY));
        playerRef.getPacketHandler().tryFlush();
        lastGlideSendNanos = now;
        if (done) {
            stopGlide();
        }
    }

    private void stopGlide() {
        if (glide != null) {
            glide.cancel(false);
            glide = null;
        }
    }

    @Override
    public synchronized void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        stopGlide();
        OPEN.remove(playerRef.getUuid(), this);
    }

    private static GraphLayout.Point parsePoint(String text) {
        int comma = text.indexOf(',');
        if (comma < 0) {
            return null;
        }
        try {
            return new GraphLayout.Point(Integer.parseInt(text.substring(0, comma).trim()),
                    Integer.parseInt(text.substring(comma + 1).trim()));
        } catch (NumberFormatException e) {
            return null; // forged event
        }
    }

    // ---- unlocking ----

    private void unlock(String id) {
        Talent talent;
        try {
            talent = graph.talent(TalentId.parse(id)).orElse(null);
        } catch (IllegalArgumentException e) {
            talent = null;
        }
        if (talent == null) {
            return; // stale or forged event, nothing to do
        }
        try {
            talents.unlock(talent.id());
        } catch (TalentException e) {
            // The overlay is only shown when unlocking is possible, so this is
            // a click that raced a page update. The tooltip already explains.
            return;
        }
        UICommandBuilder commands = new UICommandBuilder();
        updatePoints(commands);
        Set<TalentId> refreshed = new HashSet<>();
        updateNode(commands, talent);
        updateLinks(commands, talent);
        refreshed.add(talent.id());
        for (Talent dependent : dependents.getOrDefault(talent.id(), List.of())) {
            updateNode(commands, dependent);
            updateLinks(commands, dependent);
            refreshed.add(dependent.id());
        }
        // Spending points can flip the affordability of every other node.
        for (Talent other : order) {
            if (!refreshed.contains(other.id()) && nodeIndex.containsKey(other.id())) {
                updateAffordance(commands, other, talents.rank(other.id()), stateOf(other, talents.rank(other.id())));
            }
        }
        updateMinimapDots(commands);
        sendUpdate(commands, null, false);
    }

    // ---- canvas ----

    /**
     * Redraws the whole graph at the current zoom and places it under the
     * window, binding the action of every node. Bindings die with the
     * elements they were attached to, so the previous ones need no cleanup.
     */
    private void renderCanvas(UICommandBuilder commands, UIEventBuilder events) {
        commands.clear("#Graph");
        placeContent(commands);
        linkIndex.clear();
        nodeIndex.clear();
        int index = 0;
        // Links first: an empty group per talent with an incoming link.
        for (Talent talent : order) {
            if (hasLink(talent)) {
                commands.appendInline("#Graph", "Group { }");
                linkIndex.put(talent.id(), index++);
            }
        }
        for (Talent talent : order) {
            if (linkIndex.containsKey(talent.id())) {
                updateLinks(commands, talent);
            }
        }
        int size = camera.scale(GraphLayout.NODE_SIZE);
        boolean badges = camera.zoom() > BADGE_MIN_ZOOM;
        for (Talent talent : order) {
            GraphLayout.Point p = position(talent);
            nodeIndex.put(talent.id(), index++);
            String selector = nodeSelector(talent);
            commands.append("#Graph", NODE_UI);
            commands.setObject(selector + ".Anchor",
                    camera.project(p.x(), p.y(), GraphLayout.NODE_SIZE, GraphLayout.NODE_SIZE));
            int icon = camera.scale(ICON_SIZE);
            commands.setObject(selector + " #Icon.Anchor", Camera.anchor(0, 0, icon, icon));
            if (badges) {
                if (hasPips(talent)) {
                    anchorPips(commands, selector, talent.maxRank(), size);
                }
                commands.setObject(selector + " #RankBadge.Anchor",
                        Camera.anchor((size - RANK_BADGE_WIDTH) / 2, size - BADGE_HEIGHT / 2 - 2,
                                RANK_BADGE_WIDTH, BADGE_HEIGHT));
            }
            String iconPath = layout.icons().get(talent.id());
            if (iconPath != null) {
                commands.set(selector + " #Icon.AssetPath", iconPath);
            }
            updateNode(commands, talent);
            events.addEventBinding(CustomUIEventBindingType.Activating, selector + " #Action",
                    EventData.of("Unlock", talent.id().toString()), false);
        }
    }

    /** Moves the drawing of the graph so that the window shows what the camera looks at. */
    private void placeContent(UICommandBuilder commands) {
        commands.setObject("#Graph.Anchor", camera.contentAnchor());
    }

    /** Whether {@code talent} has a prerequisite on this canvas, hence a link to draw. */
    private boolean hasLink(Talent talent) {
        for (TalentId prerequisiteId : talent.prerequisites()) {
            if (graph.talent(prerequisiteId).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private void updatePoints(UICommandBuilder commands) {
        commands.set("#Points.Text", talents.availablePoints() + " points");
    }

    /** Refreshes what depends on the zoom level: its controls, and the minimap that a graph fitting whole does not need. */
    private void updateZoomControls(UICommandBuilder commands) {
        commands.set("#ZoomLevel.Text", camera.zoomPercent() + " %");
        commands.set("#ZoomIn.Disabled", !camera.canZoomIn());
        commands.set("#ZoomOut.Disabled", !camera.canZoomOut());
        commands.set("#Minimap.Visible", pannable && !camera.showsAll());
    }

    /** Refreshes the state of a node. */
    private void updateNode(UICommandBuilder commands, Talent talent) {
        if (!nodeIndex.containsKey(talent.id())) {
            return;
        }
        String selector = nodeSelector(talent);
        int rank = talents.rank(talent.id());
        NodeState current = stateOf(talent, rank);
        for (NodeState state : NodeState.values()) {
            commands.set(selector + " #" + state.element() + ".Visible", state == current);
        }
        commands.set(selector + " #Dim.Visible", current == NodeState.LOCKED);
        // Progress is the non-colour cue of the state: pips (or "3/10" text)
        // for a talent in progress, check mark once complete.
        boolean badges = camera.zoom() > BADGE_MIN_ZOOM;
        boolean maxed = current == NodeState.MAXED;
        boolean pips = badges && !maxed && hasPips(talent);
        boolean text = badges && !maxed && talent.maxRank() > 1 && !hasPips(talent);
        commands.set(selector + " #Pips.Visible", pips);
        if (pips) {
            for (int i = 1; i <= talent.maxRank(); i++) {
                commands.set(selector + " #Pip" + i + "On.Visible", i <= rank);
                commands.set(selector + " #Pip" + i + "Off.Visible", i > rank);
            }
        }
        commands.set(selector + " #RankBadge.Visible", badges && (maxed || text));
        commands.set(selector + " #Check.Visible", badges && maxed);
        commands.set(selector + " #Rank.Visible", text);
        if (text) {
            commands.set(selector + " #Rank.Text", rank + "/" + talent.maxRank());
        }
        updateAffordance(commands, talent, rank, current);
    }

    /**
     * Refreshes everything that depends on the point balance: the cost badge
     * and its colour, the {@code #Action} overlay and the tooltip wording.
     */
    private void updateAffordance(UICommandBuilder commands, Talent talent, int rank, NodeState state) {
        String selector = nodeSelector(talent);
        boolean maxed = state == NodeState.MAXED;
        boolean badges = camera.zoom() > BADGE_MIN_ZOOM;
        commands.set(selector + " #Cost.Visible", badges && !maxed);
        boolean affordable = false;
        if (!maxed) {
            int cost = talent.costOfRank(rank + 1);
            affordable = cost <= talents.availablePoints();
            commands.set(selector + " #CostOk.Visible", affordable);
            commands.set(selector + " #CostNo.Visible", !affordable);
            commands.set(selector + " #" + (affordable ? "CostOk" : "CostNo") + ".Text", String.valueOf(cost));
        }
        boolean actionable = affordable && state != NodeState.LOCKED;
        commands.set(selector + " #Action.Visible", actionable);
        // The overlay sits above the frame, so whichever is hit must carry the tooltip.
        Message tooltip = tooltip(talent, rank, state, affordable);
        commands.set(selector + " #" + state.element() + ".TooltipTextSpans", tooltip);
        if (actionable) {
            commands.set(selector + " #Action.TooltipTextSpans", tooltip);
        }
    }

    /** Clears and redraws every link ending at {@code talent}, if any. */
    private void updateLinks(UICommandBuilder commands, Talent talent) {
        if (!linkIndex.containsKey(talent.id())) {
            return;
        }
        String selector = linkSelector(talent);
        commands.clear(selector);
        GraphLayout.Point to = centerOf(talent);
        for (TalentId prerequisiteId : talent.prerequisites()) {
            Talent prerequisite = graph.talent(prerequisiteId).orElse(null);
            if (prerequisite == null) {
                continue; // cross-graph prerequisite: not on this canvas
            }
            LINKS.render(commands, selector, camera, centerOf(prerequisite), to, linkState(prerequisite, talent));
        }
    }

    // ---- minimap ----

    /**
     * Fills the minimap: one dot per talent, then a grid of invisible buttons
     * that each send the window to the canvas point under them (left click),
     * toggle follow mode (right click) or steer it while following (hover).
     * The cells show nothing on hover: the motion is meant to read as
     * continuous, not as a grid. The grid is the whole minimap, so clicking
     * beside the graph pans to its edge.
     */
    private void buildMinimap(UICommandBuilder commands, UIEventBuilder events) {
        updateMinimapDots(commands);
        int columns = (MINIMAP_WIDTH + MINIMAP_CELL - 1) / MINIMAP_CELL;
        int rows = (MINIMAP_HEIGHT + MINIMAP_CELL - 1) / MINIMAP_CELL;
        // Cells are addressed by child index: the markup grammar has no
        // room for ids with digits and separators.
        int cell = 0;
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < columns; i++) {
                commands.appendInline("#MinimapGrid", "Button { Anchor: (Left: " + i * MINIMAP_CELL
                        + ", Top: " + j * MINIMAP_CELL + ", Width: " + MINIMAP_CELL + ", Height: " + MINIMAP_CELL
                        + "); Style: (Default: (Background: #000000(0.0))); }");
                String target = Math.round(fromMinimapX((i + 0.5) * MINIMAP_CELL)) + ","
                        + Math.round(fromMinimapY((j + 0.5) * MINIMAP_CELL));
                String selector = "#MinimapGrid[" + cell++ + "]";
                events.addEventBinding(CustomUIEventBindingType.Activating, selector, EventData.of("Pan", target), false);
                events.addEventBinding(CustomUIEventBindingType.RightClicking, selector, EventData.of("Follow", target), false);
                events.addEventBinding(CustomUIEventBindingType.MouseEntered, selector, EventData.of("Enter", target), false);
            }
        }
    }

    /** Redraws every dot, coloured by state; cheap enough to do on each unlock. */
    private void updateMinimapDots(UICommandBuilder commands) {
        commands.clear("#MinimapDots");
        for (Talent talent : order) {
            GraphLayout.Point centre = centerOf(talent);
            int left = (int) Math.round(toMinimapX(centre.x())) - DOT_SIZE / 2;
            int top = (int) Math.round(toMinimapY(centre.y())) - DOT_SIZE / 2;
            NodeState state = stateOf(talent, talents.rank(talent.id()));
            commands.appendInline("#MinimapDots", "Group { Anchor: (Left: " + left + ", Top: " + top
                    + ", Width: " + DOT_SIZE + ", Height: " + DOT_SIZE + "); Background: (Color: "
                    + state.color() + "); }");
        }
    }

    /** Moves the rectangle marking the window on the minimap. */
    private void updateMinimapWindow(UICommandBuilder commands) {
        int left = clampMinimap(toMinimapX(camera.originX()), MINIMAP_WIDTH);
        int top = clampMinimap(toMinimapY(camera.originY()), MINIMAP_HEIGHT);
        int right = clampMinimap(toMinimapX(camera.rightX()), MINIMAP_WIDTH);
        int bottom = clampMinimap(toMinimapY(camera.bottomY()), MINIMAP_HEIGHT);
        int width = Math.max(1, right - left);
        int height = Math.max(1, bottom - top);
        commands.setObject("#WindowFill.Anchor", Camera.anchor(left, top, width, height));
        commands.setObject("#WindowTop.Anchor", Camera.anchor(left, top, width, 1));
        commands.setObject("#WindowBottom.Anchor", Camera.anchor(left, top + height - 1, width, 1));
        commands.setObject("#WindowLeft.Anchor", Camera.anchor(left, top, 1, height));
        commands.setObject("#WindowRight.Anchor", Camera.anchor(left + width - 1, top, 1, height));
    }

    private static int clampMinimap(double value, int max) {
        return (int) Math.round(Math.max(0, Math.min(max, value)));
    }

    private double toMinimapX(double canvasX) {
        return minimapLeft + (canvasX - layout.bounds().minX()) * minimapScale;
    }

    private double toMinimapY(double canvasY) {
        return minimapTop + (canvasY - layout.bounds().minY()) * minimapScale;
    }

    private double fromMinimapX(double minimapX) {
        return layout.bounds().minX() + (minimapX - minimapLeft) / minimapScale;
    }

    private double fromMinimapY(double minimapY) {
        return layout.bounds().minY() + (minimapY - minimapTop) / minimapScale;
    }

    // ---- state ----

    private NodeState stateOf(Talent talent, int rank) {
        if (rank >= talent.maxRank()) {
            return NodeState.MAXED;
        }
        if (rank > 0) {
            return NodeState.UNLOCKED;
        }
        return prerequisitesMet(talent) ? NodeState.AVAILABLE : NodeState.LOCKED;
    }

    private LinkState linkState(Talent prerequisite, Talent talent) {
        if (talents.rank(talent.id()) > 0) {
            return LinkState.UNLOCKED;
        }
        return talents.rank(prerequisite.id()) > 0 ? LinkState.AVAILABLE : LinkState.LOCKED;
    }

    private boolean prerequisitesMet(Talent talent) {
        for (TalentId prerequisite : talent.prerequisites()) {
            if (talents.rank(prerequisite) < 1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Bold title, muted rank line for multi-rank talents, then one line that
     * says what the player can do: red italics when nothing (and why), green
     * when the talent is unlockable or already done.
     */
    private Message tooltip(Talent talent, int rank, NodeState state, boolean affordable) {
        Message tip = Message.empty()
                .insert(Message.raw(talent.displayName()).bold(true).color(COLOR_TITLE));
        if (talent.maxRank() > 1) {
            tip.insert(Message.raw("\nRank " + rank + "/" + talent.maxRank()).color(COLOR_MUTED));
        }
        return switch (state) {
            case MAXED -> tip.insert(ok(talent.maxRank() > 1 ? "Max rank reached" : "Already unlocked"));
            case LOCKED -> {
                List<String> names = new ArrayList<>();
                for (TalentId prerequisite : talent.prerequisites()) {
                    names.add(graph.talent(prerequisite).map(Talent::displayName)
                            .orElse(prerequisite.toString()));
                }
                yield tip.insert(blocked("Requires " + String.join(", ", names)));
            }
            case AVAILABLE, UNLOCKED -> {
                int cost = talent.costOfRank(rank + 1);
                yield tip.insert(affordable
                        ? ok((rank > 0 ? "Click to upgrade" : "Click to unlock") + " (" + cost + " point(s))")
                        : blocked("Not enough points (" + cost + " needed, "
                                + talents.availablePoints() + " available)"));
            }
        };
    }

    private static Message ok(String text) {
        return Message.raw("\n" + text).color(COLOR_OK);
    }

    private static Message blocked(String text) {
        return Message.raw("\n" + text).italic(true).color(COLOR_BLOCKED);
    }

    /** Whether the progress of {@code talent} is shown as pips rather than text. */
    private static boolean hasPips(Talent talent) {
        return talent.maxRank() > 1 && talent.maxRank() <= MAX_PIPS;
    }

    /** Centres the badge holding {@code count} pips on the bottom edge of a node {@code size} wide. */
    private static void anchorPips(UICommandBuilder commands, String selector, int count, int size) {
        int width = count * PIP_PITCH - (PIP_PITCH - PIP_SIZE) + 2 * PIP_PADDING;
        commands.setObject(selector + " #Pips.Anchor", Camera.anchor((size - width) / 2,
                size - BADGE_HEIGHT / 2, width, BADGE_HEIGHT));
        for (int i = 1; i <= count; i++) {
            Anchor anchor = Camera.anchor(PIP_PADDING + (i - 1) * PIP_PITCH, (BADGE_HEIGHT - PIP_SIZE) / 2, PIP_SIZE, PIP_SIZE);
            commands.setObject(selector + " #Pip" + i + "On.Anchor", anchor);
            commands.setObject(selector + " #Pip" + i + "Off.Anchor", anchor);
        }
    }

    private GraphLayout.Point centerOf(Talent talent) {
        GraphLayout.Point p = position(talent);
        return new GraphLayout.Point(p.x() + GraphLayout.NODE_SIZE / 2, p.y() + GraphLayout.NODE_SIZE / 2);
    }

    private GraphLayout.Point position(Talent talent) {
        // A layout registered before a hot reload may miss a freshly added talent.
        return layout.positions().getOrDefault(talent.id(), new GraphLayout.Point(0, 0));
    }

    private String linkSelector(Talent talent) {
        return "#Graph[" + linkIndex.get(talent.id()) + "]";
    }

    private String nodeSelector(Talent talent) {
        return "#Graph[" + nodeIndex.get(talent.id()) + "]";
    }

    /** Visual state of a node; {@link #element()} names its frame in {@code Node.ui}, {@link #color()} its minimap dot. */
    private enum NodeState {
        LOCKED("Locked", "#4a5666"), AVAILABLE("Available", "#6fa8dc"),
        UNLOCKED("Unlocked", "#f2c94c"), MAXED("Maxed", "#3fa86f");

        private final String element;
        private final String color;

        NodeState(String element, String color) {
            this.element = element;
            this.color = color;
        }

        String element() {
            return element;
        }

        String color() {
            return color;
        }
    }

    /** Data sent back by the client when a bound element fires; exactly one field is set. */
    public static final class Event {
        static final BuilderCodec<Event> CODEC = BuilderCodec.builder(Event.class, Event::new)
                .append(new KeyedCodec<>("Unlock", Codec.STRING, false), (e, v) -> e.unlock = v, e -> e.unlock).add()
                .append(new KeyedCodec<>("Action", Codec.STRING, false), (e, v) -> e.action = v, e -> e.action).add()
                .append(new KeyedCodec<>("Zoom", Codec.STRING, false), (e, v) -> e.zoom = v, e -> e.zoom).add()
                .append(new KeyedCodec<>("Pan", Codec.STRING, false), (e, v) -> e.pan = v, e -> e.pan).add()
                .append(new KeyedCodec<>("Follow", Codec.STRING, false), (e, v) -> e.toggle = v, e -> e.toggle).add()
                .append(new KeyedCodec<>("Enter", Codec.STRING, false), (e, v) -> e.enter = v, e -> e.enter).add()
                .build();

        private String unlock;
        private String action;
        /** {@code "in"} or {@code "out"}. */
        private String zoom;
        /** Canvas point to centre on, as {@code "x,y"}. */
        private String pan;
        /** Same, from a right click: also toggles follow mode. */
        private String toggle;
        /** Canvas point under the hovered minimap cell, as {@code "x,y"}. */
        private String enter;
    }
}
