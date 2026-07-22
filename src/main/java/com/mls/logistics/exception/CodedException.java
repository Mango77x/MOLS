package com.mls.logistics.exception;

import java.util.Map;

/**
 * Base for exceptions that carry an optional machine-readable code (+
 * params) alongside their message, so the frontend can localize the error
 * instead of showing the message text verbatim. The message stays
 * mandatory as a fallback for callers that haven't been migrated to a code
 * yet, and for logs/Swagger, which only ever see English.
 */
public abstract class CodedException extends RuntimeException {

    private final String code;
    private final Map<String, Object> params;

    protected CodedException(String message) {
        super(message);
        this.code = null;
        this.params = null;
    }

    protected CodedException(String code, Map<String, Object> params, String message) {
        super(message);
        this.code = code;
        this.params = params;
    }

    public String getCode() {
        return code;
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
