package dev.galysso.talentgraph.ui;

/**
 * How a prerequisite link is drawn, derived from the ranks at both ends.
 */
public enum LinkState {
    /** The prerequisite itself is not unlocked yet. */
    LOCKED("#3a4656"),
    /** The prerequisite is met, the dependent talent can be unlocked. */
    AVAILABLE("#6fa8dc"),
    /** The dependent talent has at least one rank. */
    UNLOCKED("#3fa86f"),
    /** The prerequisite names no talent: drawn dashed, towards a ghost. */
    BROKEN("#e05a5a", true);

    private final String color;
    private final boolean dashed;

    LinkState(String color) {
        this(color, false);
    }

    LinkState(String color, boolean dashed) {
        this.color = color;
        this.dashed = dashed;
    }

    /**
     * {@return whether the line is drawn with gaps, the cue that does not rely on colour}
     */
    public boolean dashed() {
        return dashed;
    }

    /**
     * {@return the {@code #rrggbb} tint used by the renderers}
     */
    public String color() {
        return color;
    }
}
