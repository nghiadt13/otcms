package fu.fa26.sep490.g58.otcms_be.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SupabaseDatasourceGuardTest {
    @Test
    void acceptsRemoteSslPostgresUrl() {
        assertDoesNotThrow(() -> new SupabaseDatasourceGuard(
                "jdbc:postgresql://db.example.supabase.co:5432/postgres?sslmode=require").validate());
    }

    @Test
    void rejectsLocalDatabaseForSupabaseProfile() {
        assertThrows(IllegalStateException.class,
                () -> new SupabaseDatasourceGuard("jdbc:postgresql://localhost:5432/otcms_db?sslmode=require").validate());
    }

    @Test
    void rejectsRemoteDatabaseWithoutRequiredSsl() {
        assertThrows(IllegalStateException.class,
                () -> new SupabaseDatasourceGuard("jdbc:postgresql://db.example.supabase.co:5432/postgres").validate());
    }
}
