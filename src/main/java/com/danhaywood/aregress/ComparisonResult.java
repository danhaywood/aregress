package com.danhaywood.aregress;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Parsed view of the JSON exported by cfct's "Download" action.
 *
 * Only the fields aregress cares about are mapped; the rest of the (potentially large)
 * document is ignored by Gson. {@link #hasDifferences} is the pass/fail signal; the
 * per-table summaries drive the failure diagnostics logged on a mismatch.
 */
public class ComparisonResult {

    private static final Gson GSON = new Gson();

    public boolean hasDifferences;
    public List<Table> tables;

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
            String json = Files.readString(jsonFile);
            return GSON.fromJson(json, ComparisonResult.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse cfct comparison JSON at " + jsonFile, e);
        }
    }

    /** Human-readable one-line summary of the tables that differ, for failure logging. */
    public String describeDifferences() {
        if (tables == null) {
            return "(no table detail)";
        }
        return tables.stream()
                .filter(t -> t.summary != null && t.summary.hasDifferences)
                .map(t -> t.table + " (" + t.summary.differingRowCount + " differing row(s)"
                        + (t.summary.rowsOnlyInLeftCount > 0 ? ", " + t.summary.rowsOnlyInLeftCount + " only-in-left" : "")
                        + (t.summary.rowsOnlyInRightCount > 0 ? ", " + t.summary.rowsOnlyInRightCount + " only-in-right" : "")
                        + ")")
                .collect(Collectors.joining("; "));
    }
}
