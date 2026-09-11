package dev.splitedge.report.support;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;

public final class GuardedPostgres {

    private GuardedPostgres() {}

    /**
     * Validates the target database name, then migrates. Callers must not connect,
     * migrate, or insert until this method has accepted the URL.
     */
    public static DataSource connectAndMigrate(IntegrationDatabaseSettings settings, Migrator migrator) {
        IntegrationDatabaseGuard.requireTestDatabase(settings.jdbcUrl());
        DataSource dataSource = settings.dataSource();
        migrator.migrate(dataSource);
        return dataSource;
    }

    public static DataSource connectAndMigrate(IntegrationDatabaseSettings settings) {
        return connectAndMigrate(settings, GuardedPostgres::flywayMigrate);
    }

    static void flywayMigrate(DataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @FunctionalInterface
    public interface Migrator {
        void migrate(DataSource dataSource);
    }
}
