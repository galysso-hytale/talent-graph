package dev.galysso.talentgraph.ui;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

/**
 * Straight links at any angle, built from pre-rendered tiles.
 *
 * <p>{@code Lines/deg_000.png … deg_179.png} (see {@code tools/GenerateTextures.java})
 * each hold a 2 px line through the tile centre at the given angle. Tiles are
 * laid along the link close enough to overlap, and tinted per state. The
 * stretch of the link hidden under the two nodes is skipped.</p>
 */
public final class SpriteLinkRenderer implements LinkRenderer {

    /** Where the tiles live, relative to the root of the custom UI assets. */
    private static final String TILE_PATH = "Pages/TalentGraph/Lines/";
    private static final int TILE = 32;
    private static final int HALF_TILE = TILE / 2;
    /** Distance between consecutive tile centres; below {@link #TILE} so they overlap at any angle. */
    private static final int STEP = 24;

    @Override
    public void render(UICommandBuilder builder, String selector,
                       GraphLayout.Point from, GraphLayout.Point to, LinkState state) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double length = Math.hypot(dx, dy);
        if (length <= GraphLayout.NODE_SIZE) {
            return; // the nodes touch, nothing to see
        }
        String texture = TILE_PATH + String.format("deg_%03d.png", angleOf(dx, dy));
        // A tile centred within HALF_TILE of a node centre sits entirely under
        // that node; start and end just outside that range.
        double first = HALF_TILE;
        double last = length - HALF_TILE;
        int gaps = Math.max(1, (int) Math.ceil((last - first) / STEP));
        for (int i = 0; i <= gaps; i++) {
            double t = first + (last - first) * i / gaps;
            long cx = Math.round(from.x() + dx * t / length);
            long cy = Math.round(from.y() + dy * t / length);
            builder.appendInline(selector, "Group { Anchor: (Left: " + (cx - HALF_TILE)
                    + ", Top: " + (cy - HALF_TILE) + ", Width: " + TILE + ", Height: " + TILE
                    + "); Background: (TexturePath: \"" + texture + "\", Color: " + state.color() + "); }");
        }
    }

    /**
     * Angle of a direction in whole degrees within {@code [0, 180)}: a line has
     * no orientation, so opposite directions share a tile.
     */
    static int angleOf(double dx, double dy) {
        int degrees = (int) Math.round(Math.toDegrees(Math.atan2(dy, dx)));
        return Math.floorMod(degrees, 180);
    }
}
