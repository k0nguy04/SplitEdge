package dev.splitedge.report.api;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;

/**
 * Raised when a numeric request field is present but is not a JSON numeric token.
 * The field name lets the error handler emit a specific stable code.
 */
public final class StrictNumericFormatException extends DatabindException {

    private final transient String field;

    public StrictNumericFormatException(JsonParser parser, String field, String message) {
        super(parser, message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
