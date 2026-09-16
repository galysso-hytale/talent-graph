package dev.galysso.talentgraph.ui;

import dev.galysso.talentgraph.api.TalentId;

import java.util.Map;

/**
 * Where each talent of a graph sits on the page canvas, in pixels. Purely a
 * presentation concern, which is why it lives in {@code core} and not in the
 * API: a graph is valid without one, see {@link AutoLayout}.
 *
 * @param positions top-left corner of every node, keyed by talent
 * @param icons     icon asset path of the talents that declare one, e.g.
 *                  {@code UI/Custom/Pages/TalentGraph/Icons/Cleave.png}
 * @param width     canvas width in pixels
 * @param height    canvas height in pixels
 */
public record GraphLayout(Map<TalentId, Point> positions, Map<TalentId, String> icons,
                          int width, int height) {

    /** Node size in pixels; nodes are square. */
    public static final int NODE_SIZE = 72;
    /** Empty space kept around the outermost nodes. */
    public static final int MARGIN = 64;

    public GraphLayout {
        positions = Map.copyOf(positions);
        icons = Map.copyOf(icons);
    }

    /**
     * Builds a layout from explicit positions, sizing the canvas to fit them.
     *
     * @param positions top-left corner of every node
     * @return the layout
     */
    public static GraphLayout of(Map<TalentId, Point> positions) {
        int maxX = 0;
        int maxY = 0;
        for (Point p : positions.values()) {
            maxX = Math.max(maxX, p.x());
            maxY = Math.max(maxY, p.y());
        }
        return new GraphLayout(positions, Map.of(), maxX + NODE_SIZE + MARGIN, maxY + NODE_SIZE + MARGIN);
    }

    /**
     * {@return a copy of this layout with the given icons}
     *
     * @param icons icon asset path per talent
     */
    public GraphLayout withIcons(Map<TalentId, String> icons) {
        return new GraphLayout(positions, icons, width, height);
    }

    /**
     * A pixel position on the canvas.
     *
     * @param x distance from the left edge
     * @param y distance from the top edge
     */
    public record Point(int x, int y) {
    }
}
