package dev.galysso.talentgraph.ui;

import dev.galysso.talentgraph.api.TalentId;

import java.util.Map;

/**
 * Where each talent of a graph sits on the canvas, in canvas units. Purely a
 * presentation concern, which is why it lives in {@code core} and not in the
 * API: a graph is valid without one, see {@link AutoLayout}.
 *
 * <p>The canvas has no fixed size: it is whatever rectangle the nodes span,
 * plus a margin, and may extend to negative coordinates. The page shows a
 * window on it, see {@link Camera}.</p>
 *
 * @param positions top-left corner of every node, keyed by talent
 * @param icons     icon asset path of the talents that declare one, e.g.
 *                  {@code UI/Custom/Pages/TalentGraph/Icons/Cleave.png}
 * @param bounds    the rectangle spanned by the nodes and the margin
 */
public record GraphLayout(Map<TalentId, Point> positions, Map<TalentId, String> icons, Bounds bounds) {

    /** Node size in canvas units; nodes are square. */
    public static final int NODE_SIZE = 72;
    /** Empty space kept around the outermost nodes. */
    public static final int MARGIN = 64;

    public GraphLayout {
        positions = Map.copyOf(positions);
        icons = Map.copyOf(icons);
    }

    /**
     * Builds a layout from explicit positions, bounding the canvas to fit them.
     *
     * @param positions top-left corner of every node
     * @return the layout
     */
    public static GraphLayout of(Map<TalentId, Point> positions) {
        int minX = 0;
        int minY = 0;
        int maxX = 0;
        int maxY = 0;
        boolean first = true;
        for (Point p : positions.values()) {
            if (first) {
                minX = maxX = p.x();
                minY = maxY = p.y();
                first = false;
            } else {
                minX = Math.min(minX, p.x());
                minY = Math.min(minY, p.y());
                maxX = Math.max(maxX, p.x());
                maxY = Math.max(maxY, p.y());
            }
        }
        return new GraphLayout(positions, Map.of(), new Bounds(minX - MARGIN, minY - MARGIN,
                maxX + NODE_SIZE + MARGIN, maxY + NODE_SIZE + MARGIN));
    }

    /**
     * {@return a copy of this layout with the given icons}
     *
     * @param icons icon asset path per talent
     */
    public GraphLayout withIcons(Map<TalentId, String> icons) {
        return new GraphLayout(positions, icons, bounds);
    }

    /**
     * A position on the canvas.
     *
     * @param x distance from the origin along the x axis
     * @param y distance from the origin along the y axis
     */
    public record Point(int x, int y) {
    }

    /**
     * An axis-aligned rectangle of the canvas, edges included.
     *
     * @param minX left edge
     * @param minY top edge
     * @param maxX right edge
     * @param maxY bottom edge
     */
    public record Bounds(int minX, int minY, int maxX, int maxY) {

        public int width() {
            return maxX - minX;
        }

        public int height() {
            return maxY - minY;
        }
    }
}
