package dev.galysso.talentgraph.asset;

import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import dev.galysso.talentgraph.api.TalentException;
import dev.galysso.talentgraph.api.TalentGraph;
import dev.galysso.talentgraph.api.TalentGraphBuilder;
import dev.galysso.talentgraph.api.TalentId;
import dev.galysso.talentgraph.ui.AutoLayout;
import dev.galysso.talentgraph.ui.GraphLayout;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns a parsed graph file into a {@link LoadedGraph}, collecting every
 * fault instead of stopping at the first. Whatever the file says, the
 * result can be drawn: bad values are clamped, unknown prerequisites become
 * ghosts, cycles are cut. The report says what was repaired, and whether
 * the graph may be served.
 *
 * <p>Asset references are resolved here too. An icon written as one word
 * ({@code "Weapon_Sword_Copper"}) is a vanilla item icon; a path is taken
 * from the pack's own folder {@value #PACK_ROOT} unless it starts with a
 * root of {@code Common/} ({@code UI/}, {@code Icons/}) or names an asset
 * the server already knows.</p>
 */
public final class GraphLoader {

    /** Folder of {@code Common/} where pack-relative icon and background paths live. */
    public static final String PACK_ROOT = "UI/Custom/TalentGraph/";
    /** Folder of the vanilla item icons, one 64×64 PNG per item id. */
    static final String VANILLA_ICONS = "Icons/ItemsGenerated/";
    /** A canvas larger than this on a side is taken for a typo. */
    static final int MAX_CANVAS = 20_000;
    /**
     * Rough count of client widgets above which the page may stutter; to
     * calibrate at the performance checkpoint. A node is about twenty
     * widgets, a link one tile per {@value #LINK_TILE} units.
     */
    static final int WIDGET_BUDGET = 6_000;
    static final int WIDGETS_PER_NODE = 20;
    static final int LINK_TILE = 32;
    /** Distance at which a ghost is placed from the talent that requires it. */
    static final int GHOST_GAP = 160;
    private static final int[][] GHOST_OFFSETS = {
            {-GHOST_GAP, 0}, {0, -GHOST_GAP}, {-GHOST_GAP, -GHOST_GAP}, {GHOST_GAP, 0},
            {0, GHOST_GAP}, {GHOST_GAP, -GHOST_GAP}, {-GHOST_GAP, GHOST_GAP}, {GHOST_GAP, GHOST_GAP}};

    private final TalentGraphAsset asset;
    private final String fileName;
    private final List<GraphProblem> problems = new ArrayList<>();
    private final List<GraphReport.Ghost> ghosts = new ArrayList<>();
    private String namespace;
    private TalentId graphId;
    private String prefix;
    /** Talents in file order, once their id is accepted. */
    private final Map<TalentId, Entry> entries = new LinkedHashMap<>();

    private GraphLoader(TalentGraphAsset asset, String fileName) {
        this.asset = asset;
        this.fileName = fileName;
    }

    /**
     * Loads a parsed file.
     *
     * @param asset    the file as decoded by the asset store
     * @param fileName the file name, for messages
     * @return a drawable graph and the report on the file
     */
    public static LoadedGraph load(TalentGraphAsset asset, String fileName) {
        return new GraphLoader(asset, fileName).load();
    }

    private LoadedGraph load() {
        identify();
        readTalents();
        resolvePrerequisites();
        cutCycles();
        TalentGraph graph = build();
        GraphLayout layout = layout(graph);
        return new LoadedGraph(graph, layout, new GraphReport(fileName, true, problems, ghosts));
    }

    // ---- identity ----

    private void identify() {
        namespace = asset.getNamespace();
        if (!isValidPart(namespace, false)) {
            error("Namespace '" + namespace + "' may only use a-z, 0-9, '_' and '-'; using '"
                    + TalentGraphAsset.DEFAULT_NAMESPACE + "'");
            namespace = TalentGraphAsset.DEFAULT_NAMESPACE;
        }
        String path = asset.getId().toLowerCase(Locale.ROOT);
        if (!isValidPart(path, true)) {
            String repaired = sanitize(path);
            error("File name '" + asset.getId() + "' may only use letters, digits, '_' and '-'; using '"
                    + repaired + "'");
            path = repaired;
        }
        graphId = new TalentId(namespace, path);
        prefix = path + '/';
        if (asset.getName() == null || asset.getName().isBlank()) {
            warning("Missing \"Name\": showing '" + asset.getId() + "'");
        }
    }

    // ---- talents ----

    private void readTalents() {
        TalentDefinition[] definitions = asset.getTalents();
        for (int i = 0; i < definitions.length; i++) {
            TalentDefinition def = definitions[i];
            String rawId = def.getId();
            if (rawId == null || rawId.isBlank()) {
                error("Talent #" + (i + 1) + " has no \"Id\"; skipped");
                continue;
            }
            TalentId id;
            try {
                id = talentId(rawId);
            } catch (IllegalArgumentException e) {
                error("Talent \"" + rawId + "\": id may only use a-z, 0-9, '_' and '-'; skipped");
                continue;
            }
            if (entries.containsKey(id)) {
                error(id, "Talent \"" + rawId + "\" is defined twice; the second one is skipped");
                continue;
            }
            Entry entry = new Entry(def, rawId);
            entry.name = def.getName();
            if (entry.name == null || entry.name.isBlank()) {
                warning(id, "No \"Name\": showing the id");
                entry.name = rawId;
            }
            entry.maxRank = def.getMaxRank();
            if (entry.maxRank < 1) {
                error(id, "\"MaxRank\" must be at least 1, got " + entry.maxRank);
                entry.maxRank = 1;
            }
            entry.cost = costs(id, def.getCost());
            entry.icon = icon(id, def.getIcon());
            if (def.getX() != null && def.getY() != null) {
                entry.position = new GraphLayout.Point(def.getX(), def.getY());
            }
            entries.put(id, entry);
        }
        if (entries.isEmpty()) {
            warning("No talent defined");
        }
    }

    private int[] costs(TalentId id, int[] cost) {
        if (cost == null || cost.length == 0) {
            error(id, "\"Cost\" is empty; using 1");
            return new int[] {1};
        }
        int[] repaired = cost.clone();
        for (int i = 0; i < repaired.length; i++) {
            if (repaired[i] < 0) {
                error(id, "\"Cost\" has a negative value (" + repaired[i] + ") at rank " + (i + 1));
                repaired[i] = 0;
            }
        }
        return repaired;
    }

    @Nullable
    private String icon(TalentId id, @Nullable String reference) {
        if (reference == null) {
            return null;
        }
        if (reference.isBlank()) {
            warning(id, "\"Icon\" is empty");
            return null;
        }
        String resolved = resolveIcon(reference);
        if (isMissing(resolved)) {
            warning(id, "Icon not found: " + resolved);
        }
        return resolved;
    }

    // ---- prerequisites ----

    private void resolvePrerequisites() {
        for (Map.Entry<TalentId, Entry> e : entries.entrySet()) {
            TalentId id = e.getKey();
            Entry entry = e.getValue();
            for (String reference : entry.def.getRequires()) {
                if (reference == null || reference.isBlank()) {
                    error(id, "\"Requires\" has an empty entry");
                    continue;
                }
                TalentId required;
                try {
                    required = talentId(reference);
                } catch (IllegalArgumentException ex) {
                    error(id, "Requires \"" + reference + "\", which is not a valid id");
                    ghosts.add(new GraphReport.Ghost(reference, id, null));
                    continue;
                }
                if (required.equals(id)) {
                    error(id, "Requires itself");
                } else if (!entries.containsKey(required)) {
                    error(id, "Requires \"" + reference + "\", which no talent of this graph provides");
                    ghosts.add(new GraphReport.Ghost(reference, id, null));
                } else if (!entry.requires.add(required)) {
                    warning(id, "Requires \"" + reference + "\" twice");
                }
            }
        }
    }

    /** Removes one edge per cycle until none is left, so the graph can be built. */
    private void cutCycles() {
        List<TalentId> cycle;
        while ((cycle = findCycle()) != null) {
            TalentId last = cycle.get(cycle.size() - 1);
            TalentId first = cycle.get(0);
            StringBuilder sb = new StringBuilder("Cycle: ");
            for (TalentId id : cycle) {
                sb.append(entries.get(id).rawId).append(" -> ");
            }
            sb.append(entries.get(first).rawId);
            error(last, sb + "; the last requirement is ignored");
            entries.get(last).requires.remove(first);
        }
    }

    /** {@return the talents of one cycle, each requiring the next and the last requiring the first, or null} */
    @Nullable
    private List<TalentId> findCycle() {
        Map<TalentId, Integer> state = new HashMap<>();
        for (TalentId start : entries.keySet()) {
            if (state.containsKey(start)) {
                continue;
            }
            Deque<TalentId> path = new ArrayDeque<>();
            List<TalentId> cycle = visit(start, state, path);
            if (cycle != null) {
                return cycle;
            }
        }
        return null;
    }

    @Nullable
    private List<TalentId> visit(TalentId id, Map<TalentId, Integer> state, Deque<TalentId> path) {
        state.put(id, 1);
        path.addLast(id);
        for (TalentId required : entries.get(id).requires) {
            Integer mark = state.get(required);
            if (mark == null) {
                List<TalentId> cycle = visit(required, state, path);
                if (cycle != null) {
                    return cycle;
                }
            } else if (mark == 1) {
                // Back edge: the cycle is the path from `required` to `id`,
                // each requiring the next and `id` requiring `required`.
                List<TalentId> cycle = new ArrayList<>();
                boolean inside = false;
                for (TalentId onPath : path) {
                    inside |= onPath.equals(required);
                    if (inside) {
                        cycle.add(onPath);
                    }
                }
                return cycle;
            }
        }
        path.removeLast();
        state.put(id, 2);
        return null;
    }

    // ---- graph ----

    private TalentGraph build() {
        String name = asset.getName() == null || asset.getName().isBlank() ? asset.getId() : asset.getName();
        TalentGraphBuilder builder = TalentGraphBuilder.of(graphId, name);
        for (Map.Entry<TalentId, Entry> e : entries.entrySet()) {
            Entry entry = e.getValue();
            int[] cost = entry.cost;
            builder.talent(e.getKey(), entry.name, entry.maxRank,
                    rank -> cost[Math.min(rank, cost.length) - 1], entry.requires);
        }
        try {
            return builder.build();
        } catch (TalentException | IllegalArgumentException e) {
            // Should not happen after the repairs above; keep the page alive anyway.
            error("Graph rejected: " + e.getMessage());
            return TalentGraphBuilder.of(graphId, name).build();
        }
    }

    // ---- layout ----

    private GraphLayout layout(TalentGraph graph) {
        Map<TalentId, GraphLayout.Point> positions = new HashMap<>();
        int placed = 0;
        for (Map.Entry<TalentId, Entry> e : entries.entrySet()) {
            if (e.getValue().position != null) {
                positions.put(e.getKey(), e.getValue().position);
                placed++;
            }
        }
        if (placed < entries.size()) {
            if (placed > 0) {
                warning("Some talents lack \"X\"/\"Y\": automatic layout for all");
            }
            positions = new HashMap<>(AutoLayout.of(graph).positions());
        }
        placeGhosts(positions);
        List<GraphLayout.Point> ghostPositions = new ArrayList<>();
        for (GraphReport.Ghost ghost : ghosts) {
            ghostPositions.add(ghost.position());
        }
        GraphLayout layout = GraphLayout.of(positions, ghostPositions);
        String background = asset.getBackground();
        if (background != null && !background.isBlank()) {
            layout = withBackground(layout, resolveAsset(background));
        }
        Map<TalentId, String> icons = new HashMap<>();
        for (Map.Entry<TalentId, Entry> e : entries.entrySet()) {
            if (e.getValue().icon != null) {
                icons.put(e.getKey(), e.getValue().icon);
            }
        }
        layout = layout.withIcons(icons);
        GraphLayout.Bounds bounds = layout.bounds();
        if (bounds.width() > MAX_CANVAS || bounds.height() > MAX_CANVAS) {
            error("The canvas spans " + bounds.width() + "x" + bounds.height() + " units, more than "
                    + MAX_CANVAS + ": a position is probably mistyped");
        }
        int widgets = estimateWidgets(graph, layout);
        if (widgets > WIDGET_BUDGET) {
            warning("About " + widgets + " widgets to draw (nodes and link tiles), above the "
                    + WIDGET_BUDGET + " the page stays smooth with: shorten the links or split the graph");
        }
        return layout;
    }

    /** Puts each ghost next to the talent that requires it, on the first free side. */
    private void placeGhosts(Map<TalentId, GraphLayout.Point> positions) {
        List<GraphLayout.Point> taken = new ArrayList<>(positions.values());
        for (int i = 0; i < ghosts.size(); i++) {
            GraphReport.Ghost ghost = ghosts.get(i);
            GraphLayout.Point anchor = positions.getOrDefault(ghost.dependent(), new GraphLayout.Point(0, 0));
            GraphLayout.Point chosen = null;
            for (int[] offset : GHOST_OFFSETS) {
                GraphLayout.Point candidate = new GraphLayout.Point(anchor.x() + offset[0], anchor.y() + offset[1]);
                if (isFree(candidate, taken)) {
                    chosen = candidate;
                    break;
                }
            }
            if (chosen == null) {
                chosen = new GraphLayout.Point(anchor.x() - GHOST_GAP * (2 + i), anchor.y());
            }
            taken.add(chosen);
            ghosts.set(i, new GraphReport.Ghost(ghost.reference(), ghost.dependent(), chosen));
        }
    }

    private static boolean isFree(GraphLayout.Point candidate, List<GraphLayout.Point> taken) {
        for (GraphLayout.Point p : taken) {
            if (Math.abs(p.x() - candidate.x()) < GraphLayout.NODE_SIZE + 16
                    && Math.abs(p.y() - candidate.y()) < GraphLayout.NODE_SIZE + 16) {
                return false;
            }
        }
        return true;
    }

    private GraphLayout withBackground(GraphLayout layout, String path) {
        CommonAsset image = CommonAssetRegistry.getByName(path);
        if (image == null) {
            warning("Background not found: " + path);
            return layout;
        }
        int[] size;
        try {
            size = pngSize(image.getBlob().join());
        } catch (RuntimeException e) {
            size = null;
        }
        if (size == null) {
            warning("Background is not a PNG: " + path);
            return layout;
        }
        GraphLayout over = layout.withBackground(path, size[0], size[1]);
        for (Map.Entry<TalentId, GraphLayout.Point> e : layout.positions().entrySet()) {
            if (!over.bounds().containsNode(e.getValue())) {
                warning(e.getKey(), "Outside the " + size[0] + "x" + size[1] + " background");
            }
        }
        return over;
    }

    /** {@return width and height from a PNG header, or null if the bytes are not a PNG} */
    @Nullable
    static int[] pngSize(byte[] bytes) {
        if (bytes.length < 24 || (bytes[0] & 0xff) != 0x89 || bytes[1] != 'P' || bytes[2] != 'N' || bytes[3] != 'G'
                || bytes[12] != 'I' || bytes[13] != 'H' || bytes[14] != 'D' || bytes[15] != 'R') {
            return null;
        }
        int width = readInt(bytes, 16);
        int height = readInt(bytes, 20);
        return width > 0 && height > 0 ? new int[] {width, height} : null;
    }

    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) << 24 | (bytes[offset + 1] & 0xff) << 16
                | (bytes[offset + 2] & 0xff) << 8 | (bytes[offset + 3] & 0xff);
    }

    private int estimateWidgets(TalentGraph graph, GraphLayout layout) {
        int widgets = (graph.talents().size() + ghosts.size()) * WIDGETS_PER_NODE;
        for (Map.Entry<TalentId, Entry> e : entries.entrySet()) {
            GraphLayout.Point to = layout.positions().get(e.getKey());
            for (TalentId required : e.getValue().requires) {
                widgets += tiles(layout.positions().get(required), to);
            }
        }
        for (GraphReport.Ghost ghost : ghosts) {
            widgets += tiles(ghost.position(), layout.positions().get(ghost.dependent()));
        }
        return widgets;
    }

    private static int tiles(@Nullable GraphLayout.Point from, @Nullable GraphLayout.Point to) {
        if (from == null || to == null) {
            return 0;
        }
        double length = Math.hypot(to.x() - from.x(), to.y() - from.y());
        return (int) Math.max(0, (length - GraphLayout.NODE_SIZE) / LINK_TILE);
    }

    // ---- asset paths ----

    /**
     * Resolves an {@code "Icon"} value to an asset path under {@code Common/}.
     * One word names a vanilla item icon; a path follows {@link #resolveAsset}.
     */
    public static String resolveIcon(String reference) {
        if (reference.indexOf('/') < 0) {
            String name = reference.endsWith(".png") ? reference : reference + ".png";
            return VANILLA_ICONS + name;
        }
        return resolveAsset(reference);
    }

    /**
     * Resolves a path written in a graph file to an asset path under
     * {@code Common/}: as is when it starts at a root of {@code Common/} or
     * names an asset the server knows, otherwise under {@link #PACK_ROOT}.
     */
    public static String resolveAsset(String reference) {
        String path = reference.replace('\\', '/');
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.startsWith("UI/") || path.startsWith("Icons/") || CommonAssetRegistry.hasCommonAsset(path)) {
            return path;
        }
        return PACK_ROOT + path;
    }

    /** Whether the server knows no such asset; never claims so before any asset is indexed. */
    private static boolean isMissing(String path) {
        return !CommonAssetRegistry.getAllAssets().isEmpty() && !CommonAssetRegistry.hasCommonAsset(path);
    }

    // ---- helpers ----

    /**
     * Resolves a talent reference from JSON: {@code "cleave"} is local to the
     * graph, {@code "talentgraph:warrior/cleave"} is taken as is.
     */
    private TalentId talentId(String reference) {
        return reference.indexOf(':') >= 0
                ? TalentId.parse(reference)
                : new TalentId(namespace, prefix + reference.toLowerCase(Locale.ROOT));
    }

    private static boolean isValidPart(String value, boolean slashes) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-'
                    || (slashes && c == '/');
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    private static String sanitize(String value) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
            sb.append(allowed ? c : '_');
        }
        return sb.isEmpty() ? "graph" : sb.toString();
    }

    private void error(String message) {
        problems.add(GraphProblem.error(message));
    }

    private void error(TalentId talent, String message) {
        problems.add(GraphProblem.error(talent, message));
    }

    private void warning(String message) {
        problems.add(GraphProblem.warning(message));
    }

    private void warning(TalentId talent, String message) {
        problems.add(GraphProblem.warning(talent, message));
    }

    /** A talent while its file values are being checked and repaired. */
    private static final class Entry {
        final TalentDefinition def;
        final String rawId;
        String name;
        int maxRank;
        int[] cost;
        @Nullable
        String icon;
        @Nullable
        GraphLayout.Point position;
        final Set<TalentId> requires = new LinkedHashSet<>();

        Entry(TalentDefinition def, String rawId) {
            this.def = def;
            this.rawId = rawId;
        }
    }
}
