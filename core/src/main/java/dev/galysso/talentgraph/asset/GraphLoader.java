package dev.galysso.talentgraph.asset;

import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import dev.galysso.talentgraph.api.TalentException;
import dev.galysso.talentgraph.api.TalentGraph;
import dev.galysso.talentgraph.api.TalentGraphBuilder;
import dev.galysso.talentgraph.api.TalentId;
import dev.galysso.talentgraph.effect.AbilityEffect;
import dev.galysso.talentgraph.effect.EffectValidation;
import dev.galysso.talentgraph.effect.EquipmentBaseline;
import dev.galysso.talentgraph.effect.EquipmentEffect;
import dev.galysso.talentgraph.effect.ItemMatcher;
import dev.galysso.talentgraph.effect.References;
import dev.galysso.talentgraph.effect.TalentEffect;
import dev.galysso.talentgraph.ui.AutoLayout;
import dev.galysso.talentgraph.ui.GraphLayout;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
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
 *
 * <p>Effects are validated here as well, against the loaded assets given
 * as {@link References}; every fault of an effect is a warning that drops
 * or repairs the effect, never an error, so a typo in an effect keeps the
 * graph playable.</p>
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
    private final References refs;
    private final List<GraphProblem> problems = new ArrayList<>();
    private final List<GraphReport.Ghost> ghosts = new ArrayList<>();
    private String namespace;
    private TalentId graphId;
    private String prefix;
    /** Talents in file order, once their id is accepted. */
    private final Map<TalentId, Entry> entries = new LinkedHashMap<>();

    private GraphLoader(TalentGraphAsset asset, String fileName, References refs) {
        this.asset = asset;
        this.fileName = fileName;
        this.refs = refs;
    }

    /**
     * Loads a parsed file.
     *
     * @param asset    the file as decoded by the asset store
     * @param fileName the file name, for messages
     * @param refs     the loaded assets, to check what the effects name
     * @return a drawable graph and the report on the file
     */
    public static LoadedGraph load(TalentGraphAsset asset, String fileName, References refs) {
        return new GraphLoader(asset, fileName, refs).load();
    }

    private LoadedGraph load() {
        identify();
        readTalents();
        resolvePrerequisites();
        cutCycles();
        GraphEffects effects = readEffects();
        TalentGraph graph = build();
        GraphLayout layout = layout(graph);
        return new LoadedGraph(graph, layout, effects, new GraphReport(fileName, true, problems, ghosts));
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

    // ---- effects ----

    /**
     * Validates the equipment baseline and every talent's effects. Each
     * fault is a warning naming the effect by its position and type, and
     * the faulty effect is dropped: a talent keeps its other effects.
     */
    private GraphEffects readEffects() {
        EquipmentBaseline baseline = asset.getEquipment();
        if (baseline == null) {
            baseline = EquipmentBaseline.NONE;
        } else {
            baseline.validate(refs, this::warning);
        }
        Map<TalentId, List<TalentEffect>> byTalent = new LinkedHashMap<>();
        for (Map.Entry<TalentId, Entry> e : entries.entrySet()) {
            TalentId id = e.getKey();
            Entry entry = e.getValue();
            TalentEffect[] effects = entry.def.getEffects();
            List<TalentEffect> kept = new ArrayList<>();
            for (int i = 0; i < effects.length; i++) {
                TalentEffect effect = effects[i];
                if (effect == null) {
                    warning(id, "Effect #" + (i + 1) + " is null; skipped");
                    continue;
                }
                String label = "Effect #" + (i + 1) + " (" + effect.type() + "): ";
                EffectValidation validation = new EffectValidation(entry.maxRank, refs,
                        message -> warning(id, label + message));
                if (effect.validate(validation)) {
                    kept.add(effect);
                }
            }
            if (!kept.isEmpty()) {
                byTalent.put(id, List.copyOf(kept));
            }
        }
        Map<TalentId, Set<TalentId>> ancestors = ancestors();
        warnAbilities(byTalent, ancestors);
        warnIdleAllows(baseline, byTalent);
        return new GraphEffects(baseline, byTalent, ancestors);
    }

    /** The prerequisites of each talent, direct and transitive; the cycles are cut by now. */
    private Map<TalentId, Set<TalentId>> ancestors() {
        Map<TalentId, Set<TalentId>> ancestors = new HashMap<>();
        for (TalentId id : entries.keySet()) {
            ancestorsOf(id, ancestors);
        }
        return ancestors;
    }

    private Set<TalentId> ancestorsOf(TalentId id, Map<TalentId, Set<TalentId>> ancestors) {
        Set<TalentId> known = ancestors.get(id);
        if (known != null) {
            return known;
        }
        Set<TalentId> all = new LinkedHashSet<>();
        for (TalentId required : entries.get(id).requires) {
            all.add(required);
            all.addAll(ancestorsOf(required, ancestors));
        }
        ancestors.put(id, all);
        return all;
    }

    /**
     * An {@code Allow} only opens what something closes: the baseline's
     * {@code Forbidden} or a {@code Forbid} talent. One that names no item
     * any of those names does nothing, and the author probably expected
     * it to restrict. Judged within this graph: a {@code Forbid} of another
     * graph is not seen here.
     */
    private void warnIdleAllows(EquipmentBaseline baseline, Map<TalentId, List<TalentEffect>> byTalent) {
        List<ItemMatcher> closing = new ArrayList<>(baseline.forbidden());
        for (List<TalentEffect> effects : byTalent.values()) {
            for (TalentEffect effect : effects) {
                if (effect instanceof EquipmentEffect e && e.mode() == EquipmentEffect.Mode.FORBID) {
                    closing.addAll(e.items());
                }
            }
        }
        for (Map.Entry<TalentId, List<TalentEffect>> e : byTalent.entrySet()) {
            for (TalentEffect effect : e.getValue()) {
                if (!(effect instanceof EquipmentEffect allow) || allow.mode() != EquipmentEffect.Mode.ALLOW) {
                    continue;
                }
                for (ItemMatcher matcher : allow.items()) {
                    if (closing.stream().noneMatch(c -> matcher.overlaps(c, refs))) {
                        warning(e.getKey(), "Equipment \"Allow\" of \"" + matcher.written()
                                + "\": nothing forbids it (no \"Forbidden\" baseline or \"Forbid\" talent covers it),"
                                + " so the effect changes nothing");
                    }
                }
            }
        }
    }

    /**
     * What an ability silently replaces, and what it fights over. Three
     * warnings, none of which drops the effect:
     * <ol>
     *   <li>{@code Ability1} without {@code HeldItem}: the signature of
     *       every weapon is replaced, which is rarely meant.</li>
     *   <li>{@code Ability2}, {@code Ability3} or {@code Pick} with a
     *       {@code HeldItem} covering an item that defines its own entry
     *       for that slot (different from the unarmed default every item
     *       is completed with): the item's own behaviour is replaced — in
     *       vanilla, the crossbow's reload on {@code Ability3}. On the
     *       other slots replacing is the point, no warning.</li>
     *   <li>Two talents on the same slot, same {@code Priority}, neither
     *       requiring the other, whose {@code HeldItem} name a common item
     *       (no {@code HeldItem} names them all): if both are unlocked,
     *       only file order decides. A power-up is written as ranks or as
     *       a talent requiring the other; two distinct spells need disjoint
     *       items or different keys.</li>
     * </ol>
     */
    private void warnAbilities(Map<TalentId, List<TalentEffect>> byTalent, Map<TalentId, Set<TalentId>> ancestors) {
        List<Map.Entry<TalentId, AbilityEffect>> abilities = new ArrayList<>();
        for (Map.Entry<TalentId, List<TalentEffect>> e : byTalent.entrySet()) {
            for (TalentEffect effect : e.getValue()) {
                if (effect instanceof AbilityEffect ability) {
                    abilities.add(Map.entry(e.getKey(), ability));
                    warnReplaced(e.getKey(), ability);
                }
            }
        }
        for (int i = 0; i < abilities.size(); i++) {
            for (int j = i + 1; j < abilities.size(); j++) {
                warnConflict(abilities.get(i), abilities.get(j), ancestors);
            }
        }
    }

    private void warnReplaced(TalentId id, AbilityEffect ability) {
        InteractionType slot = ability.slot();
        if (slot == InteractionType.Ability1 && ability.heldItem().isEmpty()) {
            warning(id, "Ability on Ability1 without \"HeldItem\": it replaces the signature ability of every weapon");
            return;
        }
        if (slot != InteractionType.Ability2 && slot != InteractionType.Ability3 && slot != InteractionType.Pick) {
            return;
        }
        String unarmed = refs.unarmedRootInteraction(slot);
        Map<String, String> replaced = new LinkedHashMap<>();
        for (ItemMatcher matcher : ability.heldItem()) {
            Set<String> items = matcher.isTag() ? refs.itemsWithTag(matcher.tagIndex()) : Set.of(matcher.itemId());
            for (String item : items) {
                String own = refs.itemRootInteraction(item, slot);
                if (own != null && !own.equals(unarmed)) {
                    replaced.putIfAbsent(item, own);
                }
            }
        }
        if (replaced.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder("Ability on ").append(slot).append(" replaces what the item does with it: ");
        int shown = 0;
        for (Map.Entry<String, String> e : replaced.entrySet()) {
            if (shown == 3) {
                sb.append(", and ").append(replaced.size() - shown).append(" more");
                break;
            }
            if (shown > 0) {
                sb.append(", ");
            }
            sb.append(e.getKey()).append(" (").append(e.getValue()).append(')');
            shown++;
        }
        warning(id, sb.toString());
    }

    private void warnConflict(Map.Entry<TalentId, AbilityEffect> a, Map.Entry<TalentId, AbilityEffect> b,
                              Map<TalentId, Set<TalentId>> ancestors) {
        AbilityEffect first = a.getValue();
        AbilityEffect second = b.getValue();
        if (first.slot() != second.slot() || first.priority() != second.priority()) {
            return;
        }
        TalentId talentA = a.getKey();
        TalentId talentB = b.getKey();
        if (talentA.equals(talentB) || ancestors.getOrDefault(talentA, Set.of()).contains(talentB)
                || ancestors.getOrDefault(talentB, Set.of()).contains(talentA)) {
            return;
        }
        String common = commonItem(first.heldItem(), second.heldItem());
        if (common == null) {
            return;
        }
        warning(talentB, "Ability on " + first.slot() + " " + common + ", like \"" + entries.get(talentA).rawId
                + "\": if both are unlocked, file order decides. Make one require the other, or give them"
                + " disjoint \"HeldItem\" or different slots");
    }

    /** {@return how the two item conditions meet, or null when they never do} */
    @Nullable
    private String commonItem(List<ItemMatcher> a, List<ItemMatcher> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return a.isEmpty() && b.isEmpty() ? "without \"HeldItem\"" : "with any held item";
        }
        for (ItemMatcher mine : a) {
            for (ItemMatcher theirs : b) {
                if (mine.overlaps(theirs, refs)) {
                    return "with \"" + mine.written() + "\"";
                }
            }
        }
        return null;
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
