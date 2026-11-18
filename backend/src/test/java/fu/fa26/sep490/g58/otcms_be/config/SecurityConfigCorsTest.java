package fu.fa26.sep490.g58.otcms_be.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

/** The browser preflight must allow every custom header the frontend sends, or the request never leaves the page. */
class SecurityConfigCorsTest {
    @Test
    void preflightAllowsExpectedVersionHeaderUsedForOptimisticLocking() {
        SessionSecurityProperties properties = mock(SessionSecurityProperties.class);
        when(properties.patientOrigins()).thenReturn(List.of("http://localhost:3000"));
        when(properties.staffOrigins()).thenReturn(List.of());

        CorsConfiguration cors = new SecurityConfig().corsConfigurationSource(properties)
                .getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/v1/clinical/imaging-requests/x/cancellation"));

        assertThat(cors).isNotNull();
        assertThat(cors.checkHeaders(List.of("X-Expected-Version", "Content-Type", "X-CSRF-TOKEN")))
                .contains("X-Expected-Version");
    }
}
