package com.danhaywood.aregress;

import com.google.gson.Gson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Parsed view of the JSON produced by cfct — both the UI "Download" action and the
 * automation REST API (`GET /api/automation/comparison.json`) return this same format.
 *
 * Only the fields aregress cares about are mapped; the rest of the (potentially large)
 * document is ignored by Gson. {@link #hasDifferences} is the pass/fail signal; the
 * per-table summaries drive the failure diagnostics logged on a mismatch.
 */
public class ComparisonResult {

    private static final Gson GSON = new Gson();

    public boolean hasDifferences;
    /** Tables that differ between the two databases (empty when {@link #hasDifferences} is false). */
    public List<Table> differingTables;
    /** Every table that was compared for this command — empty for a no-op (no-footprint) command. */
    public List<Table> comparedTables;
    /** The command this comparison is for — used to confirm it matches the command just replayed. */
    public Command command;

    public static class Command {
        public String interactionId;
        public String timestamp;
    }

    public static class Table {
        public TableId table;
        public Summary summary;
    }

    public static class TableId {
        public String schema;
        public String name;

        @Override
        public String toString() {
            return schema + "." + name;
        }
    }

    public static class Summary {
        public int comparedColumnCount;
        public int differingRowCount;
        public int rowsOnlyInLeftCount;
        public int rowsOnlyInRightCount;
        public boolean hasDifferences;
    }

    public static ComparisonResult parse(Path jsonFile) {
        try {
            return parse(Files.readString(jsonFile));
        } catch (Exception e) {
            throw new RuntimeException("Failed to read cfct comparison JSON at " + jsonFile, e);
        }
    }

    public static ComparisonResult parse(String json) {
        try {
            return GSON.fromJson(json, ComparisonResult.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse cfct comparison JSON", e);
        }
    }

    /** Number of tables compared for this command; 0 indicates a no-footprint (no-op) command. */
    public int comparedTableCount() {
        return comparedTables == null ? 0 : comparedTables.size();
    }

    /** Human-readable one-line summary of the tables that differ, for failure logging. */
    public String describeDifferences() {
        if (differingTables == null || differingTables.isEmpty()) {
            return "(no table detail)";
        }
        return differingTables.stream()
                .map(t -> t.table + (t.summary == null ? "" : " (" + t.summary.differingRowCount + " differing row(s)"
                        + (t.summary.rowsOnlyInLeftCount > 0 ? ", " + t.summary.rowsOnlyInLeftCount + " only-in-left" : "")
                        + (t.summary.rowsOnlyInRightCount > 0 ? ", " + t.summary.rowsOnlyInRightCount + " only-in-right" : "")
                        + ")"))
                .collect(Collectors.joining("; "));
    }
}
