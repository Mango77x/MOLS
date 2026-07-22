package com.mls.logistics.exception;

import java.util.Map;

/**
 * Exception thrown when a request contains invalid data or violates business rules.
 *
 * This exception is caught by the GlobalExceptionHandler and converted
 * to a 400 Bad Request HTTP response.
 */
public class InvalidRequestException extends CodedException {

    /**
     * Constructs a new InvalidRequestException with the specified detail message.
     *
     * @param message detailed message explaining what validation failed
     */
    public InvalidRequestException(String message) {
        super(message);
    }

    /**
     * Constructs a new InvalidRequestException with a machine-readable code
     * (+ params) the frontend can translate, alongside the English message.
     *
     * @param code    stable identifier under the frontend's {@code errors.*} translations
     * @param params  values to interpolate into the translated message (may be {@code null})
     * @param message English fallback for logs/Swagger/untranslated clients
     */
    public InvalidRequestException(String code, Map<String, Object> params, String message) {
        super(code, params, message);
    }
}
