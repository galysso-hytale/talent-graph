package dev.galysso.talentgraph.ui;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;

/**
 * Straight links at any angle, built from pre-rendered tiles along integer
 * directions.
 *
 * <p>Anchors are whole pixels, so a link is drawn as a lattice line: a
 * direction {@code (a, b)} with integer components, and tile centres at
 * {@code P0 + k·(a, b)} — all integer points of the same exact line. Each
 * tile ({@code Lines/<a>_<b>.png}, see {@code tools/GenerateTextures.java})
 * holds the 2 px line at the exact angle {@code atan2(b, a)}, cut to the pixels
 * within half a step of its centre, so consecutive tiles partition the line's
 * pixels: no joint, no overlap, the result is identical to a single stroke.
 * Tiles exist as {@code .png} and {@code @2x.png} so that the client shows
 * them 1:1 at UI scale 1 and 2 alike.</p>
 *
 * <p>The direction set is finite (1424 tiles, at most 0.41° apart), so the
 * link is drawn along the nearest available direction through the midpoint of
 * the two node centres: the angular error moves both ends sideways by at most
 * {@code L/2 · sin(0.41°)}, under the nodes. Tiles are tinted per state.</p>
 *
 * <p>All of this happens in canvas units; each tile is then projected through
 * the camera, which scales it below 100 % zoom (the client resamples the
 * texture) and skips it when it falls outside the window.</p>
 */
public final class SpriteLinkRenderer implements LinkRenderer {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Where the tiles live, relative to the root of the custom UI assets. */
    private static final String TILE_PATH = "Pages/TalentGraph/Lines/";
    /** The same tiles as classpath resources, to check that they were generated. */
    private static final String TILE_RESOURCES = "/Common/UI/Custom/" + TILE_PATH;
    /** Mirrors {@code GenerateTextures.MAX_COMPONENT}. */
    private static final int MAX_COMPONENT = 32;
    /** Mirrors {@code GenerateTextures.MAX_AXIS_COMPONENT}. */
    private static final int MAX_AXIS_COMPONENT = 64;
    /** Mirrors {@code GenerateTextures.MIN_STEP}. */
    private static final int MIN_STEP = 32;
    /** How far around the midpoint to look for the integer point closest to the line. */
    private static final int MIDPOINT_WINDOW = 2;

    /** A direction with a tile: {@code (a, b)}, its angle in {@code [0, π)} and step length. */
    private record Direction(int a, int b, double angle, double step) {
        Direction(int a, int b) {
            this(a, b, lineAngle(a, b), Math.hypot(a, b));
        }

        String texture() {
            return TILE_PATH + a + "_" + b + ".png";
        }
    }

    private static final Direction[] DIRECTIONS = loadDirections();

    @Override
    public void render(UICommandBuilder builder, String selector, Camera camera,
                       GraphLayout.Point from, GraphLayout.Point to, LinkState state) {
        int dx = to.x() - from.x();
        int dy = to.y() - from.y();
        double length = Math.hypot(dx, dy);
        if (length <= GraphLayout.NODE_SIZE || DIRECTIONS.length == 0) {
            return; // the nodes touch, nothing to see
        }
        Direction direction = nearest(lineAngle(dx, dy));
        // Orient the step from `from` to `to`; the tile is the same either way.
        int sign = dx * direction.a() + dy * direction.b() >= 0 ? 1 : -1;
        int a = sign * direction.a();
        int b = sign * direction.b();
        double step = direction.step();

        // The lattice line closest to the midpoint of the two centres, so the
        // angular error is shared between the two ends. Lattice lines of this
        // direction are 1/step apart, so the closest is within 1/(2·step).
        double midX = (from.x() + to.x()) / 2.0;
        double midY = (from.y() + to.y()) / 2.0;
        int originX = 0;
        int originY = 0;
        double bestOffset = Double.MAX_VALUE;
        for (int ox = -MIDPOINT_WINDOW; ox <= MIDPOINT_WINDOW; ox++) {
            for (int oy = -MIDPOINT_WINDOW; oy <= MIDPOINT_WINDOW; oy++) {
                int px = (int) Math.round(midX) + ox;
                int py = (int) Math.round(midY) + oy;
                double offset = Math.abs((px - midX) * b - (py - midY) * a);
                if (offset < bestOffset) {
                    bestOffset = offset;
                    originX = px;
                    originY = py;
                }
            }
        }

        // Tile k covers the projections [p0 + k·step ± step/2) along the
        // link, p0 being the origin's projection from the midpoint. The
        // tiles containing the two inset points cover the stretch between
        // them, and end within step/2 + 2 ≤ 34 px of each node centre:
        // under the node, whose inscribed radius is 36.
        double p0 = ((originX - midX) * a + (originY - midY) * b) / step;
        double inset = step / 2 + 2;
        int firstTile = (int) Math.round((-length / 2 + inset - p0) / step);
        int lastTile = (int) Math.round((length / 2 - inset - p0) / step);
        int width = tileSize(a);
        int height = tileSize(b);
        String texture = direction.texture();
        for (int k = firstTile; k <= lastTile; k++) {
            int left = originX + k * a - width / 2;
            int top = originY + k * b - height / 2;
            String anchor = camera.projectMarkup(left, top, width, height);
            if (anchor != null) {
                builder.appendInline(selector, "Group { " + anchor
                        + "; Background: (TexturePath: \"" + texture + "\", Color: " + state.color() + "); }");
            }
        }
    }

    /** The direction whose angle is closest to {@code angle} (in {@code [0, π)}), angles wrapping at π. */
    private static Direction nearest(double angle) {
        int index = Arrays.binarySearch(DIRECTIONS, new Direction(0, 0, angle, 0),
                Comparator.comparingDouble(Direction::angle));
        if (index < 0) {
            index = -index - 1;
        }
        Direction best = null;
        double bestGap = Double.MAX_VALUE;
        for (int i = index - 1; i <= index; i++) {
            Direction candidate = DIRECTIONS[Math.floorMod(i, DIRECTIONS.length)];
            double gap = Math.abs(candidate.angle() - angle);
            gap = Math.min(gap, Math.PI - gap);
            if (gap < bestGap) {
                bestGap = gap;
                best = candidate;
            }
        }
        return best;
    }

    /** Angle of the line through the origin and {@code (x, y)}, in {@code [0, π)}: a line has no direction. */
    private static double lineAngle(int x, int y) {
        double angle = Math.atan2(y, x);
        if (angle < 0) {
            angle += Math.PI;
        }
        return angle == Math.PI ? 0 : angle;
    }

    /** Mirrors {@code GenerateTextures.tileSize}. */
    private static int tileSize(int component) {
        return (Math.abs(component) + 7) / 2 * 2;
    }

    /**
     * Mirrors {@code GenerateTextures.directions()} (steps of 32 to 64 px),
     * keeping only the directions whose tile is actually on the classpath,
     * sorted by angle.
     */
    private static Direction[] loadDirections() {
        List<Direction> directions = new ArrayList<>();
        int missing = 0;
        for (int a = 0; a <= MAX_AXIS_COMPONENT; a++) {
            for (int b = -MAX_AXIS_COMPONENT; b <= MAX_AXIS_COMPONENT; b++) {
                if (a == 0 && b <= 0 || gcd(a, Math.abs(b)) != 1) {
                    continue;
                }
                boolean nearAxis = a == 1 || Math.abs(b) == 1;
                if (Math.max(a, Math.abs(b)) > MAX_COMPONENT && !nearAxis) {
                    continue;
                }
                int scale = (int) Math.ceil(MIN_STEP / Math.hypot(a, b));
                Direction direction = new Direction(scale * a, scale * b);
                if (SpriteLinkRenderer.class.getResource(TILE_RESOURCES + direction.a() + "_" + direction.b() + ".png") == null) {
                    missing++;
                } else {
                    directions.add(direction);
                }
            }
        }
        if (missing > 0) {
            LOGGER.at(Level.WARNING).log("%d link tile(s) missing under %s; run tools/GenerateTextures.java",
                    missing, TILE_RESOURCES);
        }
        directions.sort(Comparator.comparingDouble(Direction::angle));
        return directions.toArray(Direction[]::new);
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }
}
