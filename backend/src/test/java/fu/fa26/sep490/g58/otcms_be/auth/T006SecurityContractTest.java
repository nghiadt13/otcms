package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.entity.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.security.CsrfTokenService;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
import static org.junit.jupiter.api.Assertions.*;

class T006SecurityContractTest {
    @Test void csrfIsDoubleSubmitAndConstantTimeComparable() { CsrfTokenService s = new CsrfTokenService(); String t = s.issue(); assertTrue(s.valid(t, t)); assertFalse(s.valid(t, t + "x")); }
    @Test void jwtContainsAndValidatesBoundPrincipalAndConfiguredExpiry() { var s = new fu.fa26.sep490.g58.otcms_be.auth.security.JwtTokenService(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC), "12345678901234567890123456789012"); UUID account=UUID.randomUUID(),session=UUID.randomUUID(); String token=s.issue(account, AccountType.PATIENT, session);var claims=s.validate(token);assertEquals(account,claims.accountId());assertEquals(session,claims.sessionId());assertEquals(900,claims.exp()-claims.iat());assertThrows(RuntimeException.class,()->s.validate(token.substring(0,token.length()-1)+"x")); }
    @Test void typedCookieAndCorsConfigurationRejectsUnsafeValues(){assertThrows(IllegalArgumentException.class,()->new SessionSecurityProperties("12345678901234567890123456789012",900,604800,"None",false,java.util.List.of(),java.util.List.of()));assertThrows(IllegalArgumentException.class,()->new SessionSecurityProperties("12345678901234567890123456789012",900,604800,"Strict",true,java.util.List.of("*"),java.util.List.of()));}
}
