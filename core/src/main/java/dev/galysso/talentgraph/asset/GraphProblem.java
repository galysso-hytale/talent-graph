package dev.galysso.talentgraph.asset;

import dev.galysso.talentgraph.api.TalentId;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * One thing wrong with a graph file, found while loading it.
 *
 * <p>An {@link Severity#ERROR} makes the graph unfit to play: it is not
 * registered, players keep the previous version. A {@link Severity#WARNING}
 * is served anyway. Both are logged and, for the admin tracking the file
 * (see {@code LiveReload}), drawn on the tree itself.</p>
 *
 * @param severity whether the graph is still served
 * @param message  what is wrong, in one line
 * @param talent   the talent at fault, or null for a problem of the whole graph
 */
public record GraphProblem(Severity severity, String message, @Nullable TalentId talent) {

    public GraphProblem {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
    }

    public static GraphProblem error(String message) {
        return new GraphProblem(Severity.ERROR, message, null);
    }

    public static GraphProblem error(TalentId talent, String message) {
        return new GraphProblem(Severity.ERROR, message, talent);
    }

    public static GraphProblem warning(String message) {
        return new GraphProblem(Severity.WARNING, message, null);
    }

    public static GraphProblem warning(TalentId talent, String message) {
        return new GraphProblem(Severity.WARNING, message, talent);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    public enum Severity {
        ERROR, WARNING
    }
}
