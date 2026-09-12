package dev.galysso.talentgraph.api;

/**
 * Thrown when a talent operation is rejected by the rules of the graph, as
 * opposed to a programming error.
 */
public class TalentException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TalentException(String message) {
        super(message);
    }

    public TalentException(String message, Throwable cause) {
        super(message, cause);
    }
}
