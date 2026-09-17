package dev.galysso.talentgraph.effect;

import javax.annotation.Nullable;

/**
 * How a numeric amount combines with the value it modifies. Shared by
 * {@link StatEffect} and {@link MovementEffect}; the JSON spelling is the
 * constant name in Hytale's own {@code CamelCase}.
 */
public enum Calculation {
    /** The amount is added: {@code 10} gives +10, {@code -10} a malus. */
    ADDITIVE("Additive"),
    /** The value is multiplied: {@code 1.2} gives +20 %, {@code 0.9} a malus. */
    MULTIPLICATIVE("Multiplicative");

    private final String id;

    Calculation(String id) {
        this.id = id;
    }

    /** {@return the spelling used in graph files} */
    public String id() {
        return id;
    }

    /** {@return the calculation written this way, ignoring case, or null} */
    @Nullable
    public static Calculation parse(@Nullable String text) {
        for (Calculation c : values()) {
            if (c.id.equalsIgnoreCase(text)) {
                return c;
            }
        }
        return null;
    }
}
