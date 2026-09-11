package dev.splitedge.report.api;

import java.math.BigDecimal;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Accepts only JSON numeric tokens. Strings, booleans, arrays, objects, and
 * non-finite values are rejected instead of being coerced or rounded.
 */
public final class StrictDecimalDeserializer extends ValueDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context) {
        JsonToken token = parser.currentToken();
        String field = parser.currentName() == null ? "value" : parser.currentName();
        if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
            if (parser.isNaN()) {
                throw new StrictNumericFormatException(
                        parser, field, field + " must be a finite JSON number");
            }
            return parser.getDecimalValue();
        }
        throw new StrictNumericFormatException(
                parser, field, field + " must be a JSON number, not " + describe(token));
    }

    private static String describe(JsonToken token) {
        if (token == null) {
            return "an absent value";
        }
        return switch (token) {
            case VALUE_STRING -> "a string";
            case VALUE_TRUE, VALUE_FALSE -> "a boolean";
            case START_ARRAY -> "an array";
            case START_OBJECT -> "an object";
            case VALUE_NULL -> "null";
            default -> "a non-numeric value";
        };
    }
}
