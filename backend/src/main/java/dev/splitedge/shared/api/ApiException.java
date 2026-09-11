package dev.splitedge.shared.api;

import java.util.List;
import java.util.Objects;

/**
 * Carries a stable client-facing error code. Messages must never contain SQL,
 * datasource URLs, credentials, exception class names, or filesystem paths.
 */
public class ApiException extends RuntimeException {

    private final transient ApiErrorCode code;
    private final transient List<ApiFieldError> fieldErrors;

    public ApiException(ApiErrorCode code, String message) {
        this(code, message, List.of());
    }

    public ApiException(ApiErrorCode code, String message, List<ApiFieldError> fieldErrors) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    public static ApiException field(ApiErrorCode code, String field, String message) {
        return new ApiException(code, message, List.of(new ApiFieldError(field, code, message)));
    }

    public ApiErrorCode code() {
        return code;
    }

    public List<ApiFieldError> fieldErrors() {
        return fieldErrors;
    }
}
