package com.mls.logistics.exception;

import java.util.Map;

/**
 * Exception thrown when a requested resource is not found in the database.
 *
 * This exception is caught by the GlobalExceptionHandler and converted
 * to a 404 Not Found HTTP response.
 */
public class ResourceNotFoundException extends CodedException {

    /**
     * Constructs a new ResourceNotFoundException with the specified detail message.
     *
     * @param message detailed message explaining which resource was not found
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new ResourceNotFoundException with a formatted message.
     *
     * @param resourceName name of the resource type (e.g., "Warehouse", "Unit")
     * @param fieldName name of the field used for lookup (e.g., "id")
     * @param fieldValue value that was not found
     */
    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue));
    }

    /**
     * @param code    stable identifier under the frontend's {@code errors.*} translations
     * @param params  values to interpolate into the translated message (may be {@code null})
     * @param message English fallback for logs/Swagger/untranslated clients
     */
    public ResourceNotFoundException(String code, Map<String, Object> params, String message) {
        super(code, params, message);
    }
}
