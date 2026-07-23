package com.mls.logistics.dto.response;

import java.util.List;

/**
 * Full result of a Sprint 20 bulk import call — used for both
 * {@code /import/preview} (nothing persisted yet) and {@code /import/commit}
 * (VALID/DUPLICATE_WARNING rows have been persisted; ERROR rows were
 * skipped), so the frontend can reuse the same result-table rendering for
 * both steps.
 *
 * @param <T> the parsed create-request DTO type (e.g. {@code CreateWarehouseRequest})
 */
public class ImportPreviewResponse<T> {

    private List<ImportRowResult<T>> rows;
    private int validCount;
    private int duplicateWarningCount;
    private int errorCount;

    public ImportPreviewResponse() {
    }

    public ImportPreviewResponse(List<ImportRowResult<T>> rows, int validCount, int duplicateWarningCount, int errorCount) {
        this.rows = rows;
        this.validCount = validCount;
        this.duplicateWarningCount = duplicateWarningCount;
        this.errorCount = errorCount;
    }

    public static <T> ImportPreviewResponse<T> from(List<ImportRowResult<T>> rows) {
        int valid = 0;
        int duplicate = 0;
        int error = 0;
        for (ImportRowResult<T> row : rows) {
            switch (row.getStatus()) {
                case VALID -> valid++;
                case DUPLICATE_WARNING -> duplicate++;
                case ERROR -> error++;
            }
        }
        return new ImportPreviewResponse<>(rows, valid, duplicate, error);
    }

    public List<ImportRowResult<T>> getRows() {
        return rows;
    }

    public void setRows(List<ImportRowResult<T>> rows) {
        this.rows = rows;
    }

    public int getValidCount() {
        return validCount;
    }

    public void setValidCount(int validCount) {
        this.validCount = validCount;
    }

    public int getDuplicateWarningCount() {
        return duplicateWarningCount;
    }

    public void setDuplicateWarningCount(int duplicateWarningCount) {
        this.duplicateWarningCount = duplicateWarningCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }
}
