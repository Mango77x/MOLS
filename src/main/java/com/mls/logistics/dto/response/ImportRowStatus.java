package com.mls.logistics.dto.response;

/**
 * Outcome of one CSV row during a bulk import (preview or commit).
 *
 * {@code DUPLICATE_WARNING} is advisory rather than blocking, since there is
 * no unique constraint on these names in the database (the single-record
 * create form only ever nudges with {@code useDuplicateNameWarning}). A
 * duplicate row still gets committed; it's flagged purely so the operator
 * can double-check before confirming, same as the single-record form's own
 * warning.
 */
public enum ImportRowStatus {
    VALID,
    DUPLICATE_WARNING,
    ERROR
}
