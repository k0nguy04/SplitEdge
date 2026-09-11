package dev.splitedge.report.support;

import java.net.URI;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Resolves integration-test connection settings from the environment.
 * Never logs or stores configuration in committed files.
 */
public final class IntegrationDatabaseSettings {

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public IntegrationDatabaseSettings(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    public static IntegrationDatabaseSettings fromEnvironment() {
        String springUrl = env("SPRING_DATASOURCE_URL");
        if (springUrl != null) {
            String username = firstEnv("SPRING_DATASOURCE_USERNAME", "POSTGRES_USER")
                    .orElseThrow(() -> new IllegalStateException(
                            "SPRING_DATASOURCE_USERNAME or POSTGRES_USER is required"));
            String password = firstEnv("SPRING_DATASOURCE_PASSWORD", "POSTGRES_PASSWORD")
                    .orElseThrow(() -> new IllegalStateException(
                            "SPRING_DATASOURCE_PASSWORD or POSTGRES_PASSWORD is required"));
            return new IntegrationDatabaseSettings(jdbcForm(springUrl), username, password);
        }
        String databaseUrl = env("DATABASE_URL");
        if (databaseUrl == null) {
            throw new IllegalStateException(
                    "Set SPRING_DATASOURCE_URL or DATABASE_URL to jdbc:postgresql://localhost:5432/"
                            + IntegrationDatabaseGuard.TEST_DATABASE);
        }
        return fromDatabaseUrl(databaseUrl);
    }

    static IntegrationDatabaseSettings fromDatabaseUrl(String databaseUrl) {
        String jdbcUrl = jdbcForm(databaseUrl);
        URI uri = URI.create(stripJdbcPrefix(databaseUrl));
        String username = uri.getUserInfo() == null ? env("POSTGRES_USER") : userFrom(uri);
        String password = uri.getUserInfo() == null ? env("POSTGRES_PASSWORD") : passwordFrom(uri);
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("DATABASE_URL must include a username or set POSTGRES_USER");
        }
        if (password == null) {
            throw new IllegalStateException("DATABASE_URL must include a password or set POSTGRES_PASSWORD");
        }
        return new IntegrationDatabaseSettings(jdbcUrl, username, password);
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(jdbcUrl);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    static String jdbcForm(String url) {
        String stripped = stripJdbcPrefix(url);
        URI uri = URI.create(stripped);
        String host = uri.getHost() == null ? "localhost" : uri.getHost();
        int port = uri.getPort() <= 0 ? 5432 : uri.getPort();
        return "jdbc:postgresql://" + host + ":" + port + "/" + IntegrationDatabaseGuard.databaseName(url);
    }

    private static String stripJdbcPrefix(String url) {
        return url.startsWith("jdbc:") ? url.substring("jdbc:".length()) : url;
    }

    private static String userFrom(URI uri) {
        String userInfo = uri.getUserInfo();
        int colon = userInfo.indexOf(':');
        return colon < 0 ? userInfo : userInfo.substring(0, colon);
    }

    private static String passwordFrom(URI uri) {
        String userInfo = uri.getUserInfo();
        int colon = userInfo.indexOf(':');
        return colon < 0 ? "" : userInfo.substring(colon + 1);
    }

    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static Optional<String> firstEnv(String... names) {
        for (String name : names) {
            String value = env(name);
            if (value != null) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
