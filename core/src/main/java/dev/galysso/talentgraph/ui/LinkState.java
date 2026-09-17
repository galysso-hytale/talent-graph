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
    UNLOCKED("#3fa86f");

    private final String color;

    LinkState(String color) {
        this.color = color;
    }

    /**
     * {@return the {@code #rrggbb} tint used by the renderers}
     */
    public String color() {
        return color;
    }
}
