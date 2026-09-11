package dev.splitedge.shared.api;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_JSON(HttpStatus.BAD_REQUEST),
    UNKNOWN_PROPERTY(HttpStatus.BAD_REQUEST),
    UNKNOWN_PLAYER(HttpStatus.NOT_FOUND),
    UNKNOWN_OPPONENT(HttpStatus.NOT_FOUND),
    UNSUPPORTED_PROP(HttpStatus.BAD_REQUEST),
    INVALID_LINE(HttpStatus.BAD_REQUEST),
    INVALID_DIRECTION(HttpStatus.BAD_REQUEST),
    INVALID_SEASON(HttpStatus.BAD_REQUEST),
    INVALID_LOCATION(HttpStatus.BAD_REQUEST),
    INVALID_RECENCY(HttpStatus.BAD_REQUEST),
    INVALID_MIN_MINUTES(HttpStatus.BAD_REQUEST),
    INVALID_PLAYER_ID(HttpStatus.BAD_REQUEST),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    DATA_INVARIANT_VIOLATION(HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ApiErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
