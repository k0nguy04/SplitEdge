package dev.splitedge.report.support;

/**
 * Parses a JDBC or libpq URL and refuses the primary {@code splitedge} database
 * before any migration or fixture work.
 */
public final class IntegrationDatabaseGuard {

    public static final String PRIMARY_DATABASE = "splitedge";
    public static final String TEST_DATABASE = "splitedge_backend_test";

    private IntegrationDatabaseGuard() {}

    public static String requireTestDatabase(String url) {
        String name = databaseName(url);
        if (PRIMARY_DATABASE.equalsIgnoreCase(name)) {
            throw new IllegalStateException(
                    "refusing to run integration tests against the primary splitedge database");
        }
        if (!TEST_DATABASE.equals(name)) {
            throw new IllegalStateException(
                    "integration tests require database " + TEST_DATABASE + ", found " + name);
        }
        return name;
    }

    public static String databaseName(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("database URL is required");
        }
        String withoutQuery = url.split("\\?", 2)[0];
        int slash = withoutQuery.lastIndexOf('/');
        if (slash < 0 || slash == withoutQuery.length() - 1) {
            throw new IllegalArgumentException("database URL does not contain a database name");
        }
        String name = withoutQuery.substring(slash + 1).strip();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("database URL does not contain a database name");
        }
        return name;
    }
}
