package com.mls.logistics.service.imports;

import com.mls.logistics.exception.InvalidRequestException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Shared CSV-parsing plumbing for the Sprint 20 bulk-import endpoints
 * (Resources/Warehouses/Units).
 *
 * <p>Two error tiers, matching the plan: a problem with the file itself
 * (unreadable, missing a required column) fails the whole request via
 * {@link InvalidRequestException} — there's nothing row-level to report.
 * A problem with one row's data is the caller's job to turn into an
 * {@code ImportRowResult} with {@code ERROR} status instead, so one bad row
 * doesn't abort the rest of the file; see {@link #get} deliberately never
 * throwing for that reason.</p>
 */
public final class CsvImportSupport {

    private CsvImportSupport() {
    }

    /**
     * Parses the uploaded file as a headered CSV, aborting the whole
     * request (not a per-row error) if the file can't be read or is
     * missing a column every row needs.
     */
    public static List<CSVRecord> parseRecords(MultipartFile file, List<String> requiredColumns) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("The uploaded file is empty.");
        }

        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreSurroundingSpaces(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            List<String> header;
            try {
                header = parser.getHeaderNames();
            } catch (IllegalStateException ex) {
                throw new InvalidRequestException("The uploaded file has no header row.");
            }
            for (String required : requiredColumns) {
                if (!header.contains(required)) {
                    throw new InvalidRequestException("CSV is missing required column '" + required
                            + "'. Expected columns: " + String.join(", ", requiredColumns));
                }
            }

            try {
                return parser.getRecords();
            } catch (RuntimeException ex) {
                // e.g. UncheckedIOException from malformed quoting the parser
                // can't recover from — a file-level problem, not a row-level one.
                throw new InvalidRequestException("Could not parse the uploaded file as CSV.");
            }
        } catch (IOException ex) {
            throw new InvalidRequestException("Could not read the uploaded file.");
        }
    }

    /** Trimmed value, or null if the column has nothing for this row. Never throws. */
    public static String get(CSVRecord record, String column) {
        if (!record.isMapped(column) || !record.isSet(column)) {
            return null;
        }
        String value = record.get(column);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Blank/absent → null; unparseable → null plus an error message appended to {@code errors}. */
    public static Double getOptionalDouble(CSVRecord record, String column, List<String> errors) {
        String raw = get(record, column);
        if (raw == null) {
            return null;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            errors.add("Column '" + column + "' is not a valid number: '" + raw + "'");
            return null;
        }
    }
}
