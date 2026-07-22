package com.mls.logistics.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standard error response structure returned by the API.
 *
 * Provides consistent error information to API consumers including
 * timestamp, HTTP status, error message, and request path.
 */
public class ErrorResponse {

    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    /** Machine-readable identifier the frontend can translate; null for anything not yet migrated. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String code;
    /** Values to interpolate into the translated message; null when {@code code} is null or needs none. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Object> params;

    /**
     * Default constructor for serialization frameworks.
     */
    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Constructs a complete error response.
     *
     * @param status HTTP status code
     * @param error HTTP status reason phrase
     * @param message detailed error message
     * @param path request path that caused the error
     */
    public ErrorResponse(int status, String error, String message, String path) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    /**
     * Constructs a complete error response including a translatable code.
     *
     * @param status HTTP status code
     * @param error HTTP status reason phrase
     * @param message detailed error message (English fallback)
     * @param path request path that caused the error
     * @param code machine-readable identifier the frontend can translate (may be null)
     * @param params values to interpolate into the translated message (may be null)
     */
    public ErrorResponse(int status, String error, String message, String path, String code, Map<String, Object> params) {
        this(status, error, message, path);
        this.code = code;
        this.params = params;
    }

    // Getters and setters

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
}