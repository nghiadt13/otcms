package fu.fa26.sep490.g58.otcms_be.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Prevents the Supabase runtime profile from silently writing to a local database. */
@Component
@Profile("supabase")
public class SupabaseDatasourceGuard {
    private final String datasourceUrl;

    public SupabaseDatasourceGuard(@Value("${spring.datasource.url}") String datasourceUrl) {
        this.datasourceUrl = datasourceUrl;
    }

    @PostConstruct
    void validate() {
        String normalized = datasourceUrl == null ? "" : datasourceUrl.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("jdbc:postgresql://")
                || normalized.contains("localhost")
                || normalized.contains("127.0.0.1")) {
            throw new IllegalStateException(
                    "The supabase profile requires a remote PostgreSQL JDBC URL; local database URLs are rejected");
        }
        if (!normalized.contains("sslmode=require")) {
            throw new IllegalStateException(
                    "The supabase profile requires sslmode=require in the PostgreSQL JDBC URL");
        }
    }
}
