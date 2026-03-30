package io.github.claudetoolkit.sql.model;

/**
 * Type of SQL content being reviewed.
 */
public enum SqlType {

    SQL("SQL Query"),
    STORED_PROCEDURE("Oracle Stored Procedure"),
    FUNCTION("Oracle Function"),
    TRIGGER("Oracle Trigger"),
    PACKAGE("Oracle Package");

    private final String displayName;

    SqlType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Auto-detect SQL type from content keywords.
     */
    public static SqlType detect(String content) {
        if (content == null) return SQL;
        String upper = content.toUpperCase().trim();
        if (upper.contains("CREATE OR REPLACE PROCEDURE") || upper.contains("CREATE PROCEDURE")) return STORED_PROCEDURE;
        if (upper.contains("CREATE OR REPLACE FUNCTION") || upper.contains("CREATE FUNCTION")) return FUNCTION;
        if (upper.contains("CREATE OR REPLACE TRIGGER") || upper.contains("CREATE TRIGGER")) return TRIGGER;
        if (upper.contains("CREATE OR REPLACE PACKAGE") || upper.contains("CREATE PACKAGE")) return PACKAGE;
        return SQL;
    }
}
