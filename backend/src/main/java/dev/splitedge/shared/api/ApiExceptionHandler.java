package dev.splitedge.shared.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.splitedge.report.InconsistentPlayerAppearanceException;
import dev.splitedge.report.api.StrictNumericFormatException;

import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Single source of client-facing error responses. Stack traces, SQL, datasource
 * URLs, credentials, exception class names, and filesystem paths are never emitted.
 *
 * <p>This advice is intentionally unscoped (no {@code basePackages}/{@code assignableTypes}
 * selector). Spring only treats a {@code @ControllerAdvice} as "global" when it has no
 * selectors; pre-dispatch failures such as an unsupported HTTP method or an unsupported
 * request Content-Type are resolved before a handler method is bound, so a scoped advice
 * would never see them and the framework's default (body-less) error handling would take
 * over instead.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(
            ApiException exception, HttpServletRequest request) {
        return respond(exception.code(), exception.getMessage(), exception.fieldErrors(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidBody(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = new ArrayList<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fieldErrors.add(new ApiFieldError(
                error.getField(),
                ApiErrorCode.VALIDATION_FAILED,
                error.getDefaultMessage() == null ? "invalid value" : error.getDefaultMessage())));
        return respond(ApiErrorCode.VALIDATION_FAILED, "request validation failed", fieldErrors, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        Throwable cause = exception.getCause();
        if (cause instanceof StrictNumericFormatException strict) {
            ApiErrorCode code = "minMinutes".equals(strict.field())
                    ? ApiErrorCode.INVALID_MIN_MINUTES
                    : ApiErrorCode.INVALID_LINE;
            String message = "minMinutes".equals(strict.field())
                    ? "minMinutes must be a JSON number from 0 through 80 with at most 3 decimal places"
                    : "line must be a JSON number from 0 through 999 with at most 3 decimal places";
            return respond(
                    code,
                    message,
                    List.of(new ApiFieldError(strict.field(), code, message)),
                    request);
        }
        if (cause instanceof UnrecognizedPropertyException unrecognized) {
            String field = fieldName(unrecognized);
            String message = "unknown property: " + field;
            return respond(
                    ApiErrorCode.UNKNOWN_PROPERTY,
                    message,
                    List.of(new ApiFieldError(field, ApiErrorCode.UNKNOWN_PROPERTY, message)),
                    request);
        }
        if (cause instanceof StreamReadException) {
            return respond(ApiErrorCode.MALFORMED_JSON, "request body is not valid JSON", List.of(), request);
        }
        if (cause instanceof JacksonException jackson) {
            String field = fieldName(jackson);
            String message = field.isEmpty() ? "request body has an invalid value" : field + " has an invalid value";
            List<ApiFieldError> fieldErrors = field.isEmpty()
                    ? List.of()
                    : List.of(new ApiFieldError(field, ApiErrorCode.VALIDATION_FAILED, message));
            return respond(ApiErrorCode.VALIDATION_FAILED, message, fieldErrors, request);
        }
        return respond(ApiErrorCode.MALFORMED_JSON, "request body is not valid JSON", List.of(), request);
    }

    @ExceptionHandler(InconsistentPlayerAppearanceException.class)
    public ResponseEntity<ApiErrorResponse> handleDataInvariant(HttpServletRequest request) {
        return respond(
                ApiErrorCode.DATA_INVARIANT_VIOLATION,
                "stored player appearance data is inconsistent",
                List.of(),
                request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpServletRequest request) {
        return respond(
                ApiErrorCode.METHOD_NOT_ALLOWED,
                "HTTP method is not supported for this endpoint",
                List.of(),
                request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(HttpServletRequest request) {
        return respond(
                ApiErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "request Content-Type is not supported",
                List.of(),
                request);
    }

    /**
     * Framework errors such as 404 and 405 keep their own status; everything else
     * falls back to a generic 500. Neither reveals internal details.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        HttpStatusCode status = exception instanceof ErrorResponse errorResponse
                ? errorResponse.getStatusCode()
                : ApiErrorCode.INTERNAL_ERROR.status();
        ApiErrorCode code =
                status.is5xxServerError() ? ApiErrorCode.INTERNAL_ERROR : ApiErrorCode.VALIDATION_FAILED;
        String message =
                status.is5xxServerError() ? "unexpected internal error" : "request could not be handled";
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(code, message, List.of(), request.getRequestURI(), Instant.now()));
    }

    private static String fieldName(JacksonException exception) {
        List<JacksonException.Reference> path = exception.getPath();
        if (path == null || path.isEmpty()) {
            return "";
        }
        String name = path.getLast().getPropertyName();
        return name == null ? "" : name;
    }

    private static ResponseEntity<ApiErrorResponse> respond(
            ApiErrorCode code, String message, List<ApiFieldError> fieldErrors, HttpServletRequest request) {
        return ResponseEntity.status(code.status())
                .body(new ApiErrorResponse(
                        code, message, fieldErrors, request.getRequestURI(), Instant.now()));
    }
}
