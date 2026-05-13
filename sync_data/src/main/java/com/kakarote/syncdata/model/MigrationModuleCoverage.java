package com.kakarote.syncdata.model;

/**
 * Describes whether a WK CRM source module is migrated by the current sync flow.
 */
public record MigrationModuleCoverage(
        String key,
        String label,
        String sourceTable,
        String targetTable,
        String status,
        Long rowCount,
        String message
) {
}
