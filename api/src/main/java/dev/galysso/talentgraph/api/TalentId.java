package dev.galysso.talentgraph.api;

import java.util.Objects;

/**
 * A namespaced identifier, e.g. {@code talentgraph:fireball}.
 *
 * <p>The namespace is owned by the plugin declaring the talent, which is what
 * keeps two unrelated add-ons from colliding on a common name like
 * {@code strength}.</p>
 *
 * @param namespace owner of the identifier, typically a mod id
 * @param path      identifier unique within {@code namespace}
 */
public record TalentId(String namespace, String path) {

    public TalentId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        requireValid(namespace, "namespace");
        requireValid(path, "path");
    }

    /**
     * Parses the {@code namespace:path} form produced by {@link #toString()}.
     *
     * @param value the string to parse
     * @return the parsed identifier
     * @throws IllegalArgumentException if {@code value} is not well formed
     */
    public static TalentId parse(String value) {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf(':');
        if (separator < 0) {
            throw new IllegalArgumentException("Missing ':' separator in talent id: " + value);
        }
        return new TalentId(value.substring(0, separator), value.substring(separator + 1));
    }

    @Override
    public String toString() {
        return namespace + ':' + path;
    }

    private static void requireValid(String value, String field) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-'
                    || (c == '/' && field.equals("path"));
            if (!allowed) {
                throw new IllegalArgumentException(
                        field + " contains an illegal character '" + c + "': " + value);
            }
        }
    }
}
