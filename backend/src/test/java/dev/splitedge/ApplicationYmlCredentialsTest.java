package dev.splitedge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Guards the tracked datasource configuration against ever reintroducing a
 * committed password literal or fallback default. Read-only: parses the packaged
 * {@code application.yml} straight from the classpath as text/property-source data.
 * Never starts a Spring context and never touches a database.
 */
class ApplicationYmlCredentialsTest {

    /**
     * Matches only the single, non-nested {@code POSTGRES_PASSWORD} alias placeholder
     * with an empty terminal fallback: {@code ${POSTGRES_PASSWORD:}}. Rejects any
     * literal fallback text such as {@code ${POSTGRES_PASSWORD:some_password}} and
     * rejects nesting any other placeholder (e.g. {@code SPRING_DATASOURCE_PASSWORD})
     * inside it, since that binding already happens automatically via Spring Boot's
     * relaxed environment-variable binding at higher precedence than this file.
     */
    private static final Pattern SAFE_PASSWORD_PLACEHOLDER = Pattern.compile("^\\$\\{POSTGRES_PASSWORD:}$");

    @Test
    void datasourcePasswordIsTheOptionalAliasPlaceholderWithNoLiteralFallback() throws Exception {
        Object password = property("spring.datasource.password");
        assertThat(password).isInstanceOf(String.class);
        assertThat((String) password)
                .as("spring.datasource.password in application.yml")
                .matches(SAFE_PASSWORD_PLACEHOLDER)
                .doesNotContain("SPRING_DATASOURCE_PASSWORD");
    }

    @Test
    void datasourceUrlAndUsernameStillResolveFromTheEnvironment() throws Exception {
        assertThat(property("spring.datasource.url").toString()).startsWith("${SPRING_DATASOURCE_URL:");
        assertThat(property("spring.datasource.username").toString()).startsWith("${POSTGRES_USER:");
    }

    @Test
    void noKnownLocalDevPasswordLiteralAppearsAnywhereInApplicationYml() throws Exception {
        String raw = new String(
                new ClassPathResource("application.yml").getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(raw).as("application.yml").doesNotContainIgnoringCase("splitedge_local");
    }

    private static Object property(String key) throws Exception {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        for (PropertySource<?> source : sources) {
            Object value = source.getProperty(key);
            if (value != null) {
                return value;
            }
        }
        throw new AssertionError("property not found on any loaded YAML document: " + key);
    }
}
