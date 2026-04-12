package com.dojostay.students.dto;

import java.util.List;

/**
 * Summary of a CSV bulk-import job. The row-level errors list is bounded so the
 * envelope does not balloon on a massive import — the full error table is persisted
 * in {@code bulk_import_jobs} and downloadable as a CSV via
 * {@link #errorReportPath}, which the controller exposes through the
 * {@code /api/students/import/{jobId}/errors} endpoint.
 */
public record BulkImportResult(
        Long jobId,
        int totalRows,
        int createdRows,
        int skippedRows,
        int failedRows,
        List<RowError> sampleErrors,
        String errorReportPath
) {
    public record RowError(int lineNumber, String code, String message) {
    }
}
