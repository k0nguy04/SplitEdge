package dev.splitedge.report;

/**
 * Exact rational number stored as a reduced integer numerator and a positive denominator.
 * Used for hit rates, averages, and percentage-point differences so repeating values such as
 * 1/3 are not approximated.
 */
public record ExactFraction(long numerator, long denominator) {

    public ExactFraction {
        if (denominator == 0) {
            throw new IllegalArgumentException("denominator must not be zero");
        }
        if (denominator < 0) {
            numerator = Math.negateExact(numerator);
            denominator = Math.negateExact(denominator);
        }
        long gcd = gcd(abs(numerator), denominator);
        numerator /= gcd;
        denominator /= gcd;
    }

    public static ExactFraction of(long numerator, long denominator) {
        return new ExactFraction(numerator, denominator);
    }

    public ExactFraction subtract(ExactFraction other) {
        long left = Math.multiplyExact(numerator, other.denominator);
        long right = Math.multiplyExact(other.numerator, denominator);
        return new ExactFraction(
                Math.subtractExact(left, right), Math.multiplyExact(denominator, other.denominator));
    }

    public ExactFraction multiply(long factor) {
        return new ExactFraction(Math.multiplyExact(numerator, factor), denominator);
    }

    private static long abs(long value) {
        if (value == Long.MIN_VALUE) {
            throw new ArithmeticException("overflow");
        }
        return Math.abs(value);
    }

    private static long gcd(long left, long right) {
        long a = left;
        long b = right;
        while (b != 0) {
            long remainder = a % b;
            a = b;
            b = remainder;
        }
        return a;
    }
}
