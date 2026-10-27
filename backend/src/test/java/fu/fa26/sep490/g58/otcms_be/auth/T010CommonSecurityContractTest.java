package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.common.security.SensitiveDataRedactor;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class T010CommonSecurityContractTest {
    @Test void redactorRemovesCredentialValuesAcrossJsonAndAuthorizationForms() {
        String input = "{\"password\":\"pw\", accessToken=access-123, refreshToken: refresh-456, "
                + "Authorization: Bearer jwt-789, otp=123456, public=ok}";
        String output = SensitiveDataRedactor.redact(input);
        assertFalse(output.contains("pw"));
        assertFalse(output.contains("access-123"));
        assertFalse(output.contains("refresh-456"));
        assertFalse(output.contains("jwt-789"));
        assertFalse(output.contains("123456"));
        assertTrue(output.contains("public=ok"));
        assertTrue(output.contains("[REDACTED]"));
    }

    @Test void nullRedactionIsNullAndNonSensitiveTextIsStable() {
        assertNull(SensitiveDataRedactor.redact(null));
        assertEquals("safe message", SensitiveDataRedactor.redact("safe message"));
    }

    @Test void validCorrelationIdIsPropagated() throws Exception {
        String id = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest(); request.addHeader(CorrelationIdFilter.HEADER, id);
        MockHttpServletResponse response = new MockHttpServletResponse(); FilterChain chain = mock(FilterChain.class);
        new CorrelationIdFilter().doFilter(request, response, chain);
        assertEquals(id, response.getHeader(CorrelationIdFilter.HEADER));
        assertEquals(id, CorrelationIdFilter.current(request));
        verify(chain).doFilter(request, response);
    }

    @Test void invalidCorrelationIdIsReplaced() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(); request.addHeader(CorrelationIdFilter.HEADER, "attacker-value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new CorrelationIdFilter().doFilter(request, response, mock(FilterChain.class));
        assertNotEquals("attacker-value", response.getHeader(CorrelationIdFilter.HEADER));
        assertDoesNotThrow(() -> UUID.fromString(response.getHeader(CorrelationIdFilter.HEADER)));
    }
}
