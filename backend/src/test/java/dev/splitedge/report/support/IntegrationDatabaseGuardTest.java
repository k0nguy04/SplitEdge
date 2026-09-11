package dev.splitedge.report.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class IntegrationDatabaseGuardTest {

    @Test
    void refusesPrimarySplitedgeDatabase() {
        assertThatThrownBy(() ->
                        IntegrationDatabaseGuard.requireTestDatabase(
                                "jdbc:postgresql://localhost:5432/splitedge"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("primary splitedge")
                .hasMessageNotContaining("splitedge_local");
    }

    @Test
    void refusesPrimaryDatabaseInLibpqUrlWithoutLeakingPassword() {
        assertThatThrownBy(() ->
                        IntegrationDatabaseGuard.requireTestDatabase(
                                "postgresql://splitedge:s3cretpass@localhost:5432/splitedge"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("primary splitedge")
                .hasMessageNotContaining("s3cretpass");
    }

    @Test
    void refusesImporterTestDatabaseName() {
        assertThatThrownBy(() ->
                        IntegrationDatabaseGuard.requireTestDatabase(
                                "jdbc:postgresql://localhost:5432/splitedge_test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("splitedge_backend_test");
    }

    @Test
    void acceptsBackendTestDatabase() {
        assertThat(IntegrationDatabaseGuard.requireTestDatabase(
                        "jdbc:postgresql://localhost:5432/splitedge_backend_test?ssl=false"))
                .isEqualTo("splitedge_backend_test");
    }

    @Test
    void refusesPrimaryDatabaseBeforeMigrateCallback() {
        AtomicBoolean migrated = new AtomicBoolean(false);
        IntegrationDatabaseSettings settings = new IntegrationDatabaseSettings(
                "jdbc:postgresql://localhost:5432/splitedge", "splitedge", "unused");
        assertThatThrownBy(() -> GuardedPostgres.connectAndMigrate(settings, dataSource -> migrated.set(true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("primary splitedge");
        assertThat(migrated).isFalse();
    }
}
