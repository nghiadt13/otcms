package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.controller.AuthExceptionHandler;
import fu.fa26.sep490.g58.otcms_be.auth.controller.StaffNetworkPolicy;
import fu.fa26.sep490.g58.otcms_be.auth.service.CredentialLoginException;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class T005ContractTest {
    @Test void staffPolicyUsesTypedCidrsAndFailsClosedOnMalformedConfiguration() {
        StaffNetworkPolicy policy = new StaffNetworkPolicy();
        policy.setAllowedCidrs(List.of("10.20.0.0/16"));
        assertTrue(policy.allows("10.20.4.5"));
        assertFalse(policy.allows("10.21.4.5"));

        policy.setAllowedCidrs(List.of("not-a-cidr"));
        assertFalse(policy.allows("10.20.4.5"));
    }

    @Test void forwardedAddressIsTrustedOnlyFromConfiguredProxy() {
        StaffNetworkPolicy policy = new StaffNetworkPolicy();
        policy.setTrustedProxyCidrs(List.of("10.0.0.0/8"));
        assertEquals("192.168.1.10", policy.clientAddress("10.1.2.3", "192.168.1.10, 10.1.2.3"));
        assertEquals("172.16.0.5", policy.clientAddress("172.16.0.5", "192.168.1.10"));
        assertEquals("192.168.1.10", policy.clientAddress("10.1.2.3", "for=192.168.1.10;proto=https, for=10.1.2.3", null));
        assertEquals("172.16.0.5", policy.clientAddress("172.16.0.5", "for=192.168.1.10", "192.0.2.1"));
    }

    @Test void authFailuresUseCanonicalSafeEnvelopeAndStatus() {
        AuthExceptionHandler handler = new AuthExceptionHandler();
        var response = handler.credential(new CredentialLoginException("AUTHENTICATION_FAILED"));
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("AUTHENTICATION_FAILED", body.error().code());
        assertEquals("Authentication failed", body.error().message());
        assertNotNull(body.error().correlationId());
    }

    @Test void rateLimitUsesCanonicalEnvelopeAnd429() {
        AuthExceptionHandler handler = new AuthExceptionHandler();
        var response = handler.credential(new CredentialLoginException("RATE_LIMIT_EXCEEDED"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("RATE_LIMIT_EXCEEDED", response.getBody().error().code());
    }

    @Test void unavailableRateLimitUsesSafe503Envelope() {
        AuthExceptionHandler handler = new AuthExceptionHandler();
        var response = handler.credential(new CredentialLoginException("RATE_LIMIT_UNAVAILABLE"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("RATE_LIMIT_UNAVAILABLE", response.getBody().error().code());
        assertEquals("Authentication service temporarily unavailable", response.getBody().error().message());
    }
}
