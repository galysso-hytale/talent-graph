package dev.galysso.talentgraph.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
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

/**
 * The in-game view of one talent graph for one player.
 *
 * <p>The canvas holds, in order, one link group per talent (its incoming
 * prerequisite links) and then one node per talent, so nodes always draw on
 * top of links. Both are addressed by child index, which is why the talent
 * order is fixed at construction. Unlocking refreshes the node, its dependents
 * and the link groups touching them, plus the affordance of every other node
 * since the point balance changed.</p>
 *
 * <p>Only a talent the player can unlock right now reacts to the cursor: its
 * {@code #Action} overlay is the sole element bound to an event. Everything
 * else is inert and explains itself through its tooltip.</p>
 */
public final class TalentGraphPage extends InteractiveCustomUIPage<TalentGraphPage.Event> {

    private static final String PAGE_UI = "Pages/TalentGraph/TalentGraphPage.ui";
    private static final String NODE_UI = "Pages/TalentGraph/Node.ui";
    // Swap for OrthogonalLinkRenderer if the tiled lines prove too heavy.
    private static final LinkRenderer LINKS = new SpriteLinkRenderer();
    private static final String ACTION_CLOSE = "Close";

    private final TalentGraph graph;
    private final GraphLayout layout;
    private final PlayerTalents talents;
    /** Talents in canvas order; index {@code i} is the link group, {@code size + i} the node. */
    private final List<Talent> order;
    private final Map<TalentId, Integer> indexOf = new HashMap<>();
    /** Talents that list each talent as a prerequisite. */
    private final Map<TalentId, List<Talent>> dependents = new HashMap<>();

    public TalentGraphPage(@Nonnull PlayerRef playerRef, TalentGraph graph, GraphLayout layout,
                           PlayerTalents talents) {
        super(playerRef, CustomPageLifetime.CanDismiss, Event.CODEC);
        this.graph = graph;
        this.layout = layout;
        this.talents = talents;
        this.order = new ArrayList<>(graph.talents());
        order.sort(Comparator.comparing(t -> t.id().toString()));
        for (int i = 0; i < order.size(); i++) {
            Talent talent = order.get(i);
            indexOf.put(talent.id(), i);
            for (TalentId prerequisite : talent.prerequisites()) {
                dependents.computeIfAbsent(prerequisite, k -> new ArrayList<>()).add(talent);
            }
        }
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
                      @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append(PAGE_UI);
        commands.set("#GraphName.Text", graph.displayName());
        updatePoints(commands);
        Anchor size = new Anchor();
        size.setWidth(Value.of(layout.width()));
        size.setHeight(Value.of(layout.height()));
        commands.setObject("#Canvas.Anchor", size);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of("Action", ACTION_CLOSE), false);

        // Links first: an empty full-size group per talent, filled below.
        for (int i = 0; i < order.size(); i++) {
            commands.appendInline("#Canvas", "Group { }");
        }
        for (Talent talent : order) {
            updateLinks(commands, talent);
        }
        for (Talent talent : order) {
            String selector = nodeSelector(talent);
            commands.append("#Canvas", NODE_UI);
            commands.setObject(selector + ".Anchor", nodeAnchor(talent));
            String icon = layout.icons().get(talent.id());
            if (icon != null) {
                commands.set(selector + " #Icon.AssetPath", icon);
            }
            updateNode(commands, talent);
            events.addEventBinding(CustomUIEventBindingType.Activating, selector + " #Action",
                    EventData.of("Unlock", talent.id().toString()), false);
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull Event event) {
        if (ACTION_CLOSE.equals(event.action)) {
            close();
            return;
        }
        if (event.unlock == null) {
            return;
        }
        Talent talent;
        try {
            talent = graph.talent(TalentId.parse(event.unlock)).orElse(null);
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
            if (!refreshed.contains(other.id())) {
                updateAffordance(commands, other, talents.rank(other.id()), stateOf(other, talents.rank(other.id())));
            }
        }
        sendUpdate(commands, null, false);
    }

    private void updatePoints(UICommandBuilder commands) {
        commands.set("#Points.Text", talents.availablePoints() + " points");
    }

    private void updateNode(UICommandBuilder commands, Talent talent) {
        String selector = nodeSelector(talent);
        int rank = talents.rank(talent.id());
        NodeState current = stateOf(talent, rank);
        for (NodeState state : NodeState.values()) {
            commands.set(selector + " #" + state.element() + ".Visible", state == current);
        }
        commands.set(selector + " #Dim.Visible", current == NodeState.LOCKED);
        boolean multiRank = talent.maxRank() > 1;
        commands.set(selector + " #RankBadge.Visible", multiRank);
        if (multiRank) {
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
        commands.set(selector + " #Cost.Visible", !maxed);
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
        String tooltip = tooltip(talent, rank, state, affordable);
        commands.set(selector + " #" + state.element() + ".TooltipText", tooltip);
        if (actionable) {
            commands.set(selector + " #Action.TooltipText", tooltip);
        }
    }

    /** Clears and redraws every link ending at {@code talent}. */
    private void updateLinks(UICommandBuilder commands, Talent talent) {
        String selector = linkSelector(talent);
        commands.clear(selector);
        GraphLayout.Point to = centerOf(talent);
        for (TalentId prerequisiteId : talent.prerequisites()) {
            Talent prerequisite = graph.talent(prerequisiteId).orElse(null);
            if (prerequisite == null) {
                continue; // cross-graph prerequisite: not on this canvas
            }
            LINKS.render(commands, selector, centerOf(prerequisite), to, linkState(prerequisite, talent));
        }
    }

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

    private String tooltip(Talent talent, int rank, NodeState state, boolean affordable) {
        StringBuilder text = new StringBuilder(talent.displayName())
                .append("\nRank ").append(rank).append('/').append(talent.maxRank());
        switch (state) {
            case MAXED -> text.append("\nMax rank reached");
            case LOCKED -> {
                text.append("\nRequires:");
                for (TalentId prerequisite : talent.prerequisites()) {
                    String name = graph.talent(prerequisite).map(Talent::displayName)
                            .orElse(prerequisite.toString());
                    text.append(' ').append(name);
                }
            }
            case AVAILABLE, UNLOCKED -> {
                int cost = talent.costOfRank(rank + 1);
                if (affordable) {
                    text.append("\nNext rank: ").append(cost).append(" point(s)");
                } else {
                    text.append("\nNot enough points (").append(cost).append(" needed, ")
                            .append(talents.availablePoints()).append(" available)");
                }
            }
        }
        return text.toString();
    }

    private Anchor nodeAnchor(Talent talent) {
        GraphLayout.Point p = position(talent);
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of(p.x()));
        anchor.setTop(Value.of(p.y()));
        anchor.setWidth(Value.of(GraphLayout.NODE_SIZE));
        anchor.setHeight(Value.of(GraphLayout.NODE_SIZE));
        return anchor;
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
        return "#Canvas[" + indexOf.get(talent.id()) + "]";
    }

    private String nodeSelector(Talent talent) {
        return "#Canvas[" + (order.size() + indexOf.get(talent.id())) + "]";
    }

    /** Visual state of a node; {@link #element()} names its button in {@code Node.ui}. */
    private enum NodeState {
        LOCKED("Locked"), AVAILABLE("Available"), UNLOCKED("Unlocked"), MAXED("Maxed");

        private final String element;

        NodeState(String element) {
            this.element = element;
        }

        String element() {
            return element;
        }
    }

    /** Data sent back by the client when a bound element is activated. */
    public static final class Event {
        static final BuilderCodec<Event> CODEC = BuilderCodec.builder(Event.class, Event::new)
                .append(new KeyedCodec<>("Unlock", Codec.STRING, false), (e, v) -> e.unlock = v, e -> e.unlock).add()
                .append(new KeyedCodec<>("Action", Codec.STRING, false), (e, v) -> e.action = v, e -> e.action).add()
                .build();

        private String unlock;
        private String action;
    }
}
