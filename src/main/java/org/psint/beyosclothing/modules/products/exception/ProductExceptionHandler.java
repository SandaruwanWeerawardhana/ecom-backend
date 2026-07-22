package org.psint.beyosclothing.modules.products.exception;

import lombok.extern.slf4j.Slf4j;
import org.psint.beyosclothing.common.dto.APIResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler for Product Module
 * Handles all exceptions thrown by product controllers
 */
@RestControllerAdvice(basePackages = "org.psint.beyosclothing.modules.products")
@Slf4j
public class ProductExceptionHandler {

    /**
     * Handle Resource Not Found Exception
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<APIResponse<Void>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.error("Resource not found: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(APIResponse.<Void>builder()
                        .success(false)
                        .message(ex.getMessage())
                        .build());
    }

    /**
     * Handle Duplicate Resource Exception
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<APIResponse<Void>> handleDuplicateResourceException(DuplicateResourceException ex) {
        log.error("Duplicate resource: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(APIResponse.<Void>builder()
                        .success(false)
                        .message(ex.getMessage())
                        .build());
    }

    /**
     * Handle Invalid Request Exception
     */
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<APIResponse<Void>> handleInvalidRequestException(InvalidRequestException ex) {
        log.error("Invalid request: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(APIResponse.<Void>builder()
                        .success(false)
                        .message(ex.getMessage())
                        .build());
    }

    /**
     * Handle Validation Errors
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<APIResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        log.error("Validation error occurred");

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(APIResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Validation failed")
                        .data(errors)
                        .build());
    }

    /**
     * Handle Generic Exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<APIResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred: ", ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(APIResponse.<Void>builder()
                        .success(false)
                        .message("An unexpected error occurred. Please try again later.")
                        .build());
    }
}

