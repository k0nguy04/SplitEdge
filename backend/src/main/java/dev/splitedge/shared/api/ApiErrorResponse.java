package dev.splitedge.shared.api;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        ApiErrorCode code,
        String message,
        List<ApiFieldError> fieldErrors,
        String path,
        Instant timestamp) {

    public ApiErrorResponse {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }
}
