package fu.fa26.sep490.g58.otcms_be.auth;

import static org.junit.jupiter.api.Assertions.*;
import fu.fa26.sep490.g58.otcms_be.config.AuthProperties;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

/** Binds the actual KAN27 test overrides without starting an application or connecting to PostgreSQL. */
class Kan27TestPropertiesTest {
    @Test void actualLoginOverridesBindACompletePositiveRateLimit() {
        var values = new HashMap<String, Object>();
        Kan27StaffAccountPostgresIntegrationTest.properties((name, supplier) -> {
            // No JWT/HMAC secret value is copied, bound or printed by this regression.
            if (name.startsWith("app.auth.login.")) values.put(name, supplier.get());
        });
        var environment = new MockEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("kan27-login", values));
        var properties = Binder.get(environment).bind("app.auth", Bindable.of(AuthProperties.class)).get();
        assertEquals(10000, properties.login().requests());
        assertEquals(60, properties.login().windowSeconds());
        for (var rate : List.of(properties.login(), properties.register(), properties.resend(), properties.refresh(),
                properties.credentialFailure())) {
            assertTrue(rate.requests() > 0);
            assertTrue(rate.windowSeconds() > 0);
        }
    }
    @Test void productionStillRejectsZeroOrNegativeLimits() {
        assertThrows(IllegalArgumentException.class, () -> new AuthProperties.RateLimit(0, 60));
        assertThrows(IllegalArgumentException.class, () -> new AuthProperties.RateLimit(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new AuthProperties.RateLimit(-1, 60));
        assertThrows(IllegalArgumentException.class, () -> new AuthProperties.RateLimit(1, -1));
    }
}
