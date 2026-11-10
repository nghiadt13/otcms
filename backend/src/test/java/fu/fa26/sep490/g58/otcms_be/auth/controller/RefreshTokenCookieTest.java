package fu.fa26.sep490.g58.otcms_be.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RefreshTokenCookieTest {
    private final SessionSecurityProperties properties = new SessionSecurityProperties(
            "secret", 900, 604800, "Strict", true, List.of(), List.of());

    @Test
    void rememberedLoginKeepsCookieForRefreshTokenLifetime() {
        var cookie = RefreshTokenCookie.issue(properties, "token", true);
        assertEquals(Duration.ofSeconds(604800), cookie.getMaxAge());
        assertTrue(cookie.isHttpOnly());
    }

    @Test
    void notRememberedLoginUsesSessionCookieWithoutMaxAge() {
        var cookie = RefreshTokenCookie.issue(properties, "token", false);
        assertEquals(Duration.ofSeconds(-1), cookie.getMaxAge());
        assertTrue(cookie.isHttpOnly());
        assertEquals("/api/auth", cookie.getPath());
    }
}
