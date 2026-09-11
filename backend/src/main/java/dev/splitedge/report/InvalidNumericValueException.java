package dev.splitedge.report;

public final class InvalidNumericValueException extends IllegalArgumentException {

    private final String field;

    public InvalidNumericValueException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
