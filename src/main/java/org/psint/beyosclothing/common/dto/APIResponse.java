package org.psint.beyosclothing.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.psint.beyosclothing.common.constants.ResponseCode;

import java.time.LocalDateTime;

/**
 * Standard API Response
 * Consistent response wrapper for all API endpoints
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class APIResponse<T> {
    private int responseCode;        // ✅ NEW: 1000, 1001, 1100, etc.
    private boolean success;
    private String message;
    private T data;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    public static <T> APIResponse<T> success(T data) {
        return APIResponse.<T>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message(ResponseCode.SUCCESS.getMessage())
                .data(data)
                .build();
    }

    public static <T> APIResponse<T> success(String message, T data) {
        return APIResponse.<T>builder()
                .responseCode(ResponseCode.SUCCESS.getCode())
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> APIResponse<T> created(T data) {
        return APIResponse.<T>builder()
                .responseCode(ResponseCode.CREATED.getCode())
                .success(true)
                .message(ResponseCode.CREATED.getMessage())
                .data(data)
                .build();
    }

    public static <T> APIResponse<T> error(ResponseCode code, String message) {
        return APIResponse.<T>builder()
                .responseCode(code.getCode())
                .success(false)
                .message(message)
                .build();
    }

    public static <T> APIResponse<T> error(ResponseCode code) {
        return APIResponse.<T>builder()
                .responseCode(code.getCode())
                .success(false)
                .message(code.getMessage())
                .build();
    }

    public static <T> APIResponse<T> error(String message) {
        return APIResponse.<T>builder()
                .responseCode(5000) // Generic error code
                .success(false)
                .message(message)
                .build();
    }
}
