package fu.fa26.sep490.g58.otcms_be.support;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalPostgresTestInitializerTest {

    @Test
    void acceptsOnlyDedicatedLoopbackPostgresDatabase() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:5432/otcms_test_db");
        environment.setActiveProfiles("test");

        assertDoesNotThrow(() -> LocalPostgresTestInitializer.validate(environment));
    }

    @Test
    void rejectsRemoteDatasourceEvenWhenTestProfileIsActive() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.example.test:5432/otcms_test_db");
        environment.setActiveProfiles("test");

        assertThrows(IllegalStateException.class, () -> LocalPostgresTestInitializer.validate(environment));
    }

    @Test
    void rejectsAnyDatabaseOtherThanDedicatedTestDatabase() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/otcms_db");
        environment.setActiveProfiles("test");

        assertThrows(IllegalStateException.class, () -> LocalPostgresTestInitializer.validate(environment));
    }

    @Test
    void rejectsLocalDatasourceWithoutTestProfile() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/otcms_test_db");

        assertThrows(IllegalStateException.class, () -> LocalPostgresTestInitializer.validate(environment));
    }

    @Test
    void rejectsSupabaseProfileEvenWhenDatasourceUrlIsLocal() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://127.0.0.1:5432/otcms_test_db");
        environment.setActiveProfiles("test", "supabase");

        assertThrows(IllegalStateException.class, () -> LocalPostgresTestInitializer.validate(environment));
    }
}
