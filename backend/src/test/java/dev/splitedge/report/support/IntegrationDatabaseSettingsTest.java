package dev.splitedge.report.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IntegrationDatabaseSettingsTest {

    @Test
    void convertsLibpqUrlToCredentialFreeJdbcUrl() {
        IntegrationDatabaseSettings settings = IntegrationDatabaseSettings.fromDatabaseUrl(
                "postgresql://splitedge:s3cretpass@localhost:5432/splitedge_backend_test");
        assertThat(settings.jdbcUrl())
                .isEqualTo("jdbc:postgresql://localhost:5432/splitedge_backend_test");
        assertThat(settings.jdbcUrl()).doesNotContain("s3cretpass");
    }
}
