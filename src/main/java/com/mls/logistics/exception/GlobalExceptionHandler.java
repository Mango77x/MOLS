package com.mls.logistics.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.validation.FieldError;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for the entire application.
 * 
 * Intercepts exceptions thrown by controllers and services,
 * converting them into appropriate HTTP responses with consistent
 * error message structures.
 * 
 * This class uses @RestControllerAdvice to handle exceptions across
 * all @RestController classes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles ResourceNotFoundException.
     * 
     * Returns 404 Not Found when a requested entity doesn't exist.
     *
     * @param ex the exception that was thrown
     * @param request the web request during which the exception occurred
     * @return ResponseEntity with error details and 404 status
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex,
            WebRequest request) {

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * Handles InvalidRequestException.
     * 
     * Returns 400 Bad Request when business rules are violated.
     *
     * @param ex the exception that was thrown
     * @param request the web request during which the exception occurred
     * @return ResponseEntity with error details and 400 status
     */
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequestException(
            InvalidRequestException ex,
            WebRequest request) {

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles all other exceptions not explicitly handled.
     * 
     * Returns 500 Internal Server Error for unexpected exceptions.
     * This is a fallback handler to ensure the API never returns
     * unformatted error responses.
     *
     * @param ex the exception that was thrown
     * @param request the web request during which the exception occurred
     * @return ResponseEntity with error details and 500 status
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex,
            WebRequest request) {

        // Spring MVC exceptions (405 Method Not Allowed, 406, 415, ...) carry
        // their own status — preserve it instead of masking it as a 500.
        if (ex instanceof org.springframework.web.ErrorResponse springError) {
            HttpStatus status = HttpStatus.valueOf(springError.getStatusCode().value());
            ErrorResponse body = new ErrorResponse(
                    status.value(),
                    status.getReasonPhrase(),
                    status.getReasonPhrase(),
                    request.getDescription(false).replace("uri=", "")
            );
            return new ResponseEntity<>(body, status);
        }

        // Log the full exception server-side; never echo internal details
        // (class names, SQL, stack fragments) back to the client.
        log.error("Unhandled exception processing {}", request.getDescription(false), ex);

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "An unexpected error occurred. Please try again or contact an administrator.",
                request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handles optimistic-locking conflicts.
     *
     * Returns 409 Conflict when two requests modified the same row
     * concurrently (e.g. two simultaneous stock adjustments). The losing
     * request should be retried against the fresh state.
     *
     * @param ex      the optimistic locking failure
     * @param request the web request during which the exception occurred
     * @return ResponseEntity with error details and 409 status
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(
            ObjectOptimisticLockingFailureException ex,
            WebRequest request) {

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                "The record was modified by another operation at the same time. Please retry.",
                request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    /**
     * Handles validation errors from @Valid annotations.
     * 
     * Returns 400 Bad Request with detailed field-level error messages.
     *
     * @param ex the validation exception
     * @param request the web request
     * @return ResponseEntity with validation errors and 400 status
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex,
            WebRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", java.time.LocalDateTime.now());
        errorResponse.put("status", HttpStatus.BAD_REQUEST.value());
        errorResponse.put("error", HttpStatus.BAD_REQUEST.getReasonPhrase());
        errorResponse.put("message", "Validation failed");
        errorResponse.put("fieldErrors", fieldErrors);
        errorResponse.put("path", request.getDescription(false).replace("uri=", ""));

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles InsufficientStockException.
     *
     * Returns 409 Conflict when a stock operation cannot be completed
     * due to insufficient available quantity.
     *
     * @param ex      the exception that was thrown
     * @param request the web request during which the exception occurred
     * @return ResponseEntity with error details and 409 status
     */
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientStockException(
            InsufficientStockException ex,
            WebRequest request) {

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }
}