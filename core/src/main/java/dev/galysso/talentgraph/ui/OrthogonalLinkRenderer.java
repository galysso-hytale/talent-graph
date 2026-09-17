package dev.galysso.talentgraph.ui;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

/**
 * Fallback renderer using only plain coloured rectangles: a horizontal run
 * from the prerequisite, then a vertical run into the dependent talent. Two
 * elements per link whatever its length, at the price of right angles.
 */
public final class OrthogonalLinkRenderer implements LinkRenderer {

    private static final int THICKNESS = 2;

    @Override
    public void render(UICommandBuilder builder, String selector, Camera camera,
                       GraphLayout.Point from, GraphLayout.Point to, LinkState state) {
        if (from.y() != to.y()) {
            int top = Math.min(from.y(), to.y());
            int height = Math.abs(to.y() - from.y());
            rectangle(builder, selector, camera, to.x() - THICKNESS / 2, top, THICKNESS, height, state);
        }
        if (from.x() != to.x()) {
            int left = Math.min(from.x(), to.x());
            int width = Math.abs(to.x() - from.x()) + THICKNESS / 2;
            rectangle(builder, selector, camera, left, from.y() - THICKNESS / 2, width, THICKNESS, state);
        }
    }

    private static void rectangle(UICommandBuilder builder, String selector, Camera camera,
                                  int left, int top, int width, int height, LinkState state) {
        builder.appendInline(selector, "Group { " + camera.projectMarkup(left, top, width, height)
                + "; Background: (Color: " + state.color() + "); }");
    }
}
