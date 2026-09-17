package dev.galysso.talentgraph.asset;

import dev.galysso.talentgraph.api.TalentId;
import dev.galysso.talentgraph.ui.GraphLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Everything found wrong while loading a graph file, in the order found.
 *
 * <p>Two shapes. A file that does not even parse gives a report with
 * {@link #parsed()} false and a single error naming the line and column;
 * nothing else could be read. A file that parses gives one problem per
 * fault, the graph having been repaired enough to be drawn: a prerequisite
 * that names no talent becomes a {@link Ghost} node drawn at the end of a
 * broken link.</p>
 *
 * @param fileName the file the report is about, e.g. {@code Warrior.json}
 * @param parsed   whether the file could be read at all
 * @param problems the faults, worst first is not guaranteed
 * @param ghosts   the unknown talents referenced as prerequisites
 */
public record GraphReport(String fileName, boolean parsed, List<GraphProblem> problems, List<Ghost> ghosts) {

    public GraphReport {
        Objects.requireNonNull(fileName, "fileName");
        problems = List.copyOf(problems);
        ghosts = List.copyOf(ghosts);
    }

    /** {@return a report with nothing to say} */
    public static GraphReport clean(String fileName) {
        return new GraphReport(fileName, true, List.of(), List.of());
    }

    /** {@return the report of a file that could not be parsed} */
    public static GraphReport unparsable(String fileName, String message) {
        return new GraphReport(fileName, false, List.of(GraphProblem.error(message)), List.of());
    }

    public boolean isEmpty() {
        return problems.isEmpty();
    }

    public boolean hasErrors() {
        return problems.stream().anyMatch(GraphProblem::isError);
    }

    public int errorCount() {
        return (int) problems.stream().filter(GraphProblem::isError).count();
    }

    public int warningCount() {
        return problems.size() - errorCount();
    }

    /** {@return the problems of one talent, in order} */
    public List<GraphProblem> of(TalentId talent) {
        List<GraphProblem> own = new ArrayList<>();
        for (GraphProblem problem : problems) {
            if (talent.equals(problem.talent())) {
                own.add(problem);
            }
        }
        return own;
    }

    /** {@return the ghosts standing in for the missing prerequisites of a talent} */
    public List<Ghost> ghostsOf(TalentId dependent) {
        List<Ghost> own = new ArrayList<>();
        for (Ghost ghost : ghosts) {
            if (ghost.dependent().equals(dependent)) {
                own.add(ghost);
            }
        }
        return own;
    }

    /** One line for the chat and the banner: "2 errors, 1 warning". */
    public String summary() {
        if (!parsed) {
            return "cannot be read";
        }
        int errors = errorCount();
        int warnings = warningCount();
        if (errors == 0 && warnings == 0) {
            return "no problem";
        }
        StringBuilder sb = new StringBuilder();
        if (errors > 0) {
            sb.append(errors).append(errors == 1 ? " error" : " errors");
        }
        if (warnings > 0) {
            if (errors > 0) {
                sb.append(", ");
            }
            sb.append(warnings).append(warnings == 1 ? " warning" : " warnings");
        }
        return sb.toString();
    }

    /**
     * A talent named as a prerequisite that no talent of the graph provides.
     * Drawn as a hollow node so the author sees where the typo points.
     *
     * @param reference the prerequisite as written in the file
     * @param dependent the talent that requires it
     * @param position  where to draw it, chosen next to the dependent
     */
    public record Ghost(String reference, TalentId dependent, GraphLayout.Point position) {
    }
}
