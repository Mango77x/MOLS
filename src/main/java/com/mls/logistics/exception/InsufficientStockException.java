package com.mls.logistics.exception;

import java.util.Map;

/**
 * Exception thrown when a requested quantity exceeds available stock.
 *
 * Thrown by StockService when an adjustment would result in negative stock,
 * and by OrderItemService when an order item requests more than what is available.
 * Caught by GlobalExceptionHandler and converted to 409 Conflict.
 */
public class InsufficientStockException extends CodedException {

    public InsufficientStockException(String message) {
        super(message);
    }

    /**
     * @param code    stable identifier under the frontend's {@code errors.*} translations
     * @param params  values to interpolate into the translated message (may be {@code null})
     * @param message English fallback for logs/Swagger/untranslated clients
     */
    public InsufficientStockException(String code, Map<String, Object> params, String message) {
        super(code, params, message);
    }
}
