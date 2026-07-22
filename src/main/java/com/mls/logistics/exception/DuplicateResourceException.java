package com.mls.logistics.exception;

import java.util.Map;

/**
 * Exception thrown when a request would violate a uniqueness constraint
 * (e.g. a stock row for a resource/warehouse pair that already exists).
 *
 * Caught by GlobalExceptionHandler and converted to 409 Conflict.
 */
public class DuplicateResourceException extends CodedException {

    public DuplicateResourceException(String message) {
        super(message);
    }

    /**
     * @param code    stable identifier under the frontend's {@code errors.*} translations
     * @param params  values to interpolate into the translated message (may be {@code null})
     * @param message English fallback for logs/Swagger/untranslated clients
     */
    public DuplicateResourceException(String code, Map<String, Object> params, String message) {
        super(code, params, message);
    }
}
