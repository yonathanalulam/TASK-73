package com.dojostay.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standard response envelope for all DojoStay API endpoints.
 *
 * <p>Either {@code data} is populated (success) or {@code error} is populated (failure).
 * The {@code traceId} field carries the per-request correlation id so clients can quote it
 * when reporting issues.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error,
        String traceId
) {

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        return new ApiResponse<>(true, data, null, traceId);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return ok(data, CorrelationIdHolder.get());
    }

    public static ApiResponse<Void> empty() {
        return new ApiResponse<>(true, null, null, CorrelationIdHolder.get());
    }

    public static <T> ApiResponse<T> failure(ApiError error, String traceId) {
        return new ApiResponse<>(false, null, error, traceId);
    }

    public static <T> ApiResponse<T> failure(ApiError error) {
        return failure(error, CorrelationIdHolder.get());
    }
}
