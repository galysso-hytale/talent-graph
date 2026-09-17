package dev.galysso.talentgraph.ui;

import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;

/**
 * The window the page shows on the canvas: a rectangle of fixed size in
 * screen units, placed over the canvas at one of a few zoom levels.
 *
 * <p>The client reports neither the wheel nor the cursor position, so the
 * window only ever moves through explicit requests: {@link #centerOn} and
 * {@link #approach} from the minimap and {@link #zoomIn}/{@link #zoomOut}
 * from the header. The whole canvas is drawn once per zoom level in
 * <em>scaled</em> coordinates, relative to the top-left corner of the layout
 * bounds: a canvas point {@code p} lands at {@code (p − bounds.min) × zoom}.
 * Moving the window then only moves that drawing: {@link #contentAnchor}
 * places it under the window so that the origin, the canvas point under the
 * window's top-left corner, sits at its corner.</p>
 *
 * <p>The centre can reach any point of the layout bounds, so every node can
 * be brought to the middle, but never goes past them. On an axis where the
 * whole canvas fits in the window, the centre is pinned to the middle of the
 * canvas: there is nothing to pan.</p>
 */
final class Camera {

    /** Zoom levels, from the closest to the widest. */
    static final double[] ZOOM_LEVELS = {1.0, 0.75, 0.5, 1.0 / 3};

    private final int width;
    private final int height;
    private final GraphLayout.Bounds bounds;
    private int level;
    private double centerX;
    private double centerY;

    /**
     * @param width  window width in screen units
     * @param height window height in screen units
     * @param bounds the canvas rectangle the window may look at
     */
    Camera(int width, int height, GraphLayout.Bounds bounds) {
        this.width = width;
        this.height = height;
        this.bounds = bounds;
        centerOn(bounds.minX() + bounds.width() / 2.0, bounds.minY() + bounds.height() / 2.0);
    }

    double zoom() {
        return ZOOM_LEVELS[level];
    }

    /** {@return where the window is and how close, to hand over to a page rebuilt on the same graph} */
    View view() {
        return new View(level, centerX, centerY);
    }

    /** Puts the window back where a previous camera was, within the bounds of this one. */
    void restore(View view) {
        level = Math.max(0, Math.min(ZOOM_LEVELS.length - 1, view.level()));
        centerOn(view.centerX(), view.centerY());
    }

    /**
     * A snapshot of the window.
     *
     * @param level   index in {@link #ZOOM_LEVELS}
     * @param centerX canvas x at the middle of the window
     * @param centerY canvas y at the middle of the window
     */
    record View(int level, double centerX, double centerY) {
    }

    /** {@return the zoom as a percentage, for display} */
    int zoomPercent() {
        return (int) Math.round(zoom() * 100);
    }

    boolean canZoomIn() {
        return level > 0;
    }

    boolean canZoomOut() {
        return level < ZOOM_LEVELS.length - 1;
    }

    /** Zooms one level closer around the centre; {@return whether anything changed} */
    boolean zoomIn() {
        if (!canZoomIn()) {
            return false;
        }
        level--;
        centerOn(centerX, centerY);
        return true;
    }

    /** Zooms one level wider around the centre; {@return whether anything changed} */
    boolean zoomOut() {
        if (!canZoomOut()) {
            return false;
        }
        level++;
        centerOn(centerX, centerY);
        return true;
    }

    /** Moves the window so that the canvas point {@code (x, y)} is at its centre, within bounds. */
    void centerOn(double x, double y) {
        centerX = clampX(x);
        centerY = clampY(y);
    }

    /**
     * Moves the centre a fraction of the way towards the canvas point
     * {@code (x, y)}, within bounds, and snaps onto it once closer than
     * {@code snap} on both axes.
     *
     * @return whether the centre is now on the point
     */
    boolean approach(double x, double y, double fraction, double snap) {
        double targetX = clampX(x);
        double targetY = clampY(y);
        double dx = targetX - centerX;
        double dy = targetY - centerY;
        if (Math.abs(dx) <= snap && Math.abs(dy) <= snap) {
            centerX = targetX;
            centerY = targetY;
            return true;
        }
        centerX += dx * fraction;
        centerY += dy * fraction;
        return false;
    }

    /** Whether the whole canvas fits in the window at the current zoom: nothing to pan. */
    boolean showsAll() {
        return bounds.width() * zoom() <= width && bounds.height() * zoom() <= height;
    }

    /** Whether the centre is within {@code snap} of the canvas point {@code (x, y)}, within bounds. */
    boolean isAt(double x, double y, double snap) {
        return Math.abs(clampX(x) - centerX) <= snap && Math.abs(clampY(y) - centerY) <= snap;
    }

    private double clampX(double x) {
        return clamp(x, bounds.minX(), bounds.maxX(), width / zoom());
    }

    private double clampY(double y) {
        return clamp(y, bounds.minY(), bounds.maxY(), height / zoom());
    }

    private static double clamp(double value, int min, int max, double windowExtent) {
        if (max - min <= windowExtent) {
            return (min + max) / 2.0;
        }
        return Math.max(min, Math.min(max, value));
    }

    /** {@return the canvas x under the left edge of the window} */
    double originX() {
        return centerX - width / (2 * zoom());
    }

    /** {@return the canvas y under the top edge of the window} */
    double originY() {
        return centerY - height / (2 * zoom());
    }

    /** {@return the canvas x under the right edge of the window} */
    double rightX() {
        return centerX + width / (2 * zoom());
    }

    /** {@return the canvas y under the bottom edge of the window} */
    double bottomY() {
        return centerY + height / (2 * zoom());
    }

    /** {@return a canvas x in scaled coordinates} */
    int toScaledX(double canvasX) {
        return (int) Math.round((canvasX - bounds.minX()) * zoom());
    }

    /** {@return a canvas y in scaled coordinates} */
    int toScaledY(double canvasY) {
        return (int) Math.round((canvasY - bounds.minY()) * zoom());
    }

    /** {@return a length in screen units} */
    int scale(int canvasLength) {
        return (int) Math.round(canvasLength * zoom());
    }

    /**
     * The anchor of the scaled drawing of the canvas inside the window: its
     * full size, offset so that the origin lands on the window's corner.
     */
    Anchor contentAnchor() {
        return anchor(-toScaledX(originX()), -toScaledY(originY()),
                scale(bounds.width()), scale(bounds.height()));
    }

    /**
     * The anchor of a canvas rectangle in scaled coordinates. Both edges are
     * rounded separately so that adjacent rectangles stay adjacent.
     */
    Anchor project(int left, int top, int width, int height) {
        int scaledLeft = toScaledX(left);
        int scaledTop = toScaledY(top);
        return anchor(scaledLeft, scaledTop, Math.max(1, toScaledX(left + width) - scaledLeft),
                Math.max(1, toScaledY(top + height) - scaledTop));
    }

    /** Same as {@link #project(int, int, int, int)}, as inline markup. */
    String projectMarkup(int left, int top, int width, int height) {
        int scaledLeft = toScaledX(left);
        int scaledTop = toScaledY(top);
        return "Anchor: (Left: " + scaledLeft + ", Top: " + scaledTop
                + ", Width: " + Math.max(1, toScaledX(left + width) - scaledLeft)
                + ", Height: " + Math.max(1, toScaledY(top + height) - scaledTop) + ")";
    }

    /** An anchor in screen units, as the client expects it. */
    static Anchor anchor(int left, int top, int width, int height) {
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of(left));
        anchor.setTop(Value.of(top));
        anchor.setWidth(Value.of(width));
        anchor.setHeight(Value.of(height));
        return anchor;
    }
}
