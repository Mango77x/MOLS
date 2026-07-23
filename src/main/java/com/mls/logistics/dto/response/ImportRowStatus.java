package com.mls.logistics.dto.response;

/**
 * Outcome of one CSV row during a Sprint 20 bulk import (preview or commit).
 *
 * {@code DUPLICATE_WARNING} is advisory, not blocking: there is no unique
 * constraint on these names in the database (the single-record create form
 * only ever nudges with {@code useDuplicateNameWarning}), so a duplicate row
 * still gets committed — it's flagged purely so the operator can double-check
 * before confirming, same as the single-record form's own warning.
 */
public enum ImportRowStatus {
    VALID,
    DUPLICATE_WARNING,
    ERROR
}
