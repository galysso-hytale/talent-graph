package dev.galysso.talentgraph.ui;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

/**
 * Draws one prerequisite link on the page canvas.
 *
 * <p>The UI language has no line primitive, so a link is a set of positioned
 * elements appended into a container group. Implementations only emit
 * self-contained inline markup: nothing needs a follow-up {@code set}, which
 * keeps a partial refresh down to one {@code clear} plus the new elements.
 * Geometry is computed in canvas units and scaled through the camera.</p>
 */
public interface LinkRenderer {

    /**
     * Appends the elements of a link to a container.
     *
     * @param builder  the command builder of the page update
     * @param selector the group receiving the elements, in scaled canvas coordinates
     * @param camera   the zoom to draw at
     * @param from     centre of the prerequisite node, in canvas units
     * @param to       centre of the dependent node, in canvas units
     * @param state    what the link should look like
     */
    void render(UICommandBuilder builder, String selector, Camera camera,
                GraphLayout.Point from, GraphLayout.Point to, LinkState state);
}
