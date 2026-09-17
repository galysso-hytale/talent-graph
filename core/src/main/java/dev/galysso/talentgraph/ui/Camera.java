package dev.galysso.talentgraph.ui;

import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;

/**
 * The window the page shows on the canvas: a rectangle of fixed size in
 * screen units, placed over the canvas at one of a few zoom levels.
 *
 * <p>The client reports neither the wheel nor the cursor position, so the
 * window only ever moves through explicit requests: {@link #centerOn} from
 * the minimap and {@link #zoomIn}/{@link #zoomOut} from the header. Screen
 * coordinates are relative to the top-left corner of the window: a canvas
 * point {@code p} lands at {@code (p − origin) × zoom}, where the origin is
 * the canvas point under that corner.</p>
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

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    double zoom() {
        return ZOOM_LEVELS[level];
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
        centerX = clamp(x, bounds.minX(), bounds.maxX(), width / zoom());
        centerY = clamp(y, bounds.minY(), bounds.maxY(), height / zoom());
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

    int toScreenX(double canvasX) {
        return (int) Math.round((canvasX - originX()) * zoom());
    }

    int toScreenY(double canvasY) {
        return (int) Math.round((canvasY - originY()) * zoom());
    }

    /** {@return a length in screen units} */
    int scale(int canvasLength) {
        return (int) Math.round(canvasLength * zoom());
    }

    /** Whether the canvas rectangle overlaps the window. */
    boolean shows(int left, int top, int width, int height) {
        return left < rightX() && left + width > originX() && top < bottomY() && top + height > originY();
    }

    /**
     * The anchor of a canvas rectangle in screen units, or null when the
     * rectangle lies outside the window. Both edges are rounded separately
     * so that adjacent rectangles stay adjacent.
     */
    Anchor project(int left, int top, int width, int height) {
        if (!shows(left, top, width, height)) {
            return null;
        }
        int screenLeft = toScreenX(left);
        int screenTop = toScreenY(top);
        return anchor(screenLeft, screenTop, Math.max(1, toScreenX(left + width) - screenLeft),
                Math.max(1, toScreenY(top + height) - screenTop));
    }

    /** Same as {@link #project(int, int, int, int)}, as inline markup. */
    String projectMarkup(int left, int top, int width, int height) {
        if (!shows(left, top, width, height)) {
            return null;
        }
        int screenLeft = toScreenX(left);
        int screenTop = toScreenY(top);
        return "Anchor: (Left: " + screenLeft + ", Top: " + screenTop
                + ", Width: " + Math.max(1, toScreenX(left + width) - screenLeft)
                + ", Height: " + Math.max(1, toScreenY(top + height) - screenTop) + ")";
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
