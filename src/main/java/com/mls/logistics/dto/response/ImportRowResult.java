package com.mls.logistics.dto.response;

import java.util.List;

/**
 * One row's outcome from a Sprint 20 bulk import (preview or commit).
 *
 * @param <T> the parsed create-request DTO type (e.g. {@code CreateWarehouseRequest})
 */
public class ImportRowResult<T> {

    /** 1-based, matching the CSV's data rows (the header is not counted). */
    private int rowNumber;

    private ImportRowStatus status;

    /** Validation/parse failure messages; empty unless status is ERROR. */
    private List<String> errors;

    /** The parsed row, when parseable — null for a row that failed to parse at all. */
    private T data;

    public ImportRowResult() {
    }

    public ImportRowResult(int rowNumber, ImportRowStatus status, List<String> errors, T data) {
        this.rowNumber = rowNumber;
        this.status = status;
        this.errors = errors;
        this.data = data;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    public ImportRowStatus getStatus() {
        return status;
    }

    public void setStatus(ImportRowStatus status) {
        this.status = status;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
