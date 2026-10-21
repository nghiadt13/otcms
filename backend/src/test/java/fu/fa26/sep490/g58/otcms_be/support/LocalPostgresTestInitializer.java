package fu.fa26.sep490.g58.otcms_be.support;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.net.URI;
import java.util.Arrays;

/** Rejects Spring test contexts unless their effective datasource is the local OTCMS test database. */
public final class LocalPostgresTestInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        validate(applicationContext.getEnvironment());
    }

    static void validate(ConfigurableEnvironment environment) {
        boolean testProfile = Arrays.asList(environment.getActiveProfiles()).contains("test");
        boolean supabaseProfile = Arrays.asList(environment.getActiveProfiles()).contains("supabase");
        String jdbcUrl = environment.getProperty("spring.datasource.url", "");

        if (!testProfile || supabaseProfile || !isAllowedLocalTestUrl(jdbcUrl)) {
            throw new IllegalStateException(
                    "Spring integration tests require the test profile and a loopback PostgreSQL database named otcms_test_db");
        }
    }

    private static boolean isAllowedLocalTestUrl(String jdbcUrl) {
        if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
            return false;
        }

        try {
            URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
            String host = uri.getHost();
            String database = uri.getPath();
            boolean loopback = "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host)
                    || "::1".equals(host);
            return loopback && uri.getUserInfo() == null && "/otcms_test_db".equals(database);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
