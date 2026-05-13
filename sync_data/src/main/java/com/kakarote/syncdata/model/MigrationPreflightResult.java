package com.kakarote.syncdata.model;

import java.util.List;
import java.util.Map;

/**
 * Preflight result shown before a tenant-bound migration is started.
 */
public record MigrationPreflightResult(
        Long tenantId,
        Long companyId,
        boolean ready,
        List<PreflightIssue> errors,
        List<PreflightIssue> warnings,
        List<MigrationModuleCoverage> modules,
        Map<String, Long> rowCounts,
        RerunInfo rerun,
        CleanupInfo cleanup,
        IncrementalCapability incremental
) {

    public record PreflightIssue(String code, String message, String module) {
    }

    public record RerunInfo(boolean existingBinding, boolean existingMappings, Long mappingCount, String message) {
    }

    public record CleanupInfo(boolean enabled, String message) {
    }

    public record IncrementalCapability(boolean applicationAvailable, String status, String message) {
    }
}
