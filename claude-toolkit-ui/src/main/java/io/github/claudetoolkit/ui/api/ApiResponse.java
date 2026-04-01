package io.github.claudetoolkit.ui.api;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Standard JSON envelope for all REST API responses.
 *
 * <pre>
 * Success: { "success": true,  "data": {...}, "error": null,  "timestamp": "..." }
 * Failure: { "success": false, "data": null,  "error": "...", "timestamp": "..." }
 * </pre>
 */
public class ApiResponse<T> {

    private final boolean success;
    private final T       data;
    private final String  error;
    private final String  timestamp;

    private ApiResponse(boolean success, T data, String error) {
        this.success   = success;
        this.data      = data;
        this.error     = error;
        this.timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, null, message);
    }

    public boolean isSuccess()   { return success; }
    public T getData()           { return data; }
    public String getError()     { return error; }
    public String getTimestamp() { return timestamp; }
}
