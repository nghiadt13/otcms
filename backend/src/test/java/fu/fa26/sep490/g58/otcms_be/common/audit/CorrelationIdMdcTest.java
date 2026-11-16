package fu.fa26.sep490.g58.otcms_be.common.audit;

import static org.junit.jupiter.api.Assertions.*;

import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.ServletException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdMdcTest {
    @Test
    void propagatesCorrelationAndClearsItEvenWhenRequestFails() throws Exception {
        String id = UUID.randomUUID().toString();
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, id);
        var response = new MockHttpServletResponse();
        assertThrows(ServletException.class, () -> new CorrelationIdFilter().doFilter(request, response, (req, res) -> {
            assertEquals(id, MDC.get(CorrelationIdFilter.MDC_KEY));
            throw new ServletException("Synthetic request failure");
        }));
        assertEquals(id, response.getHeader(CorrelationIdFilter.HEADER));
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    @Test
    void replacesInvalidInputAndRestoresPreviousContext() throws Exception {
        MDC.put(CorrelationIdFilter.MDC_KEY, "previous");
        try {
            var request = new MockHttpServletRequest();
            request.addHeader(CorrelationIdFilter.HEADER, "unsafe\nvalue");
            new CorrelationIdFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                assertDoesNotThrow(() -> UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY)));
                assertNotEquals("previous", MDC.get(CorrelationIdFilter.MDC_KEY));
            });
            assertEquals("previous", MDC.get(CorrelationIdFilter.MDC_KEY));
        } finally { MDC.remove(CorrelationIdFilter.MDC_KEY); }
    }
}
