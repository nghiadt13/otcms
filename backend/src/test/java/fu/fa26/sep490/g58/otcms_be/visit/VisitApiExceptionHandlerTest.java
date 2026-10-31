package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitApiExceptionHandler;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class VisitApiExceptionHandlerTest {
    @Test
    void mapsPostgresExclusionViolationToSlotUnavailableConflict() {
        var handler = new VisitApiExceptionHandler();
        var request = new MockHttpServletRequest();
        var failure = new DataIntegrityViolationException("conflict", new SQLException("occupied", "23P01"));

        var response = handler.databaseFailure(failure, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("SLOT_UNAVAILABLE", response.getBody().error().code());
    }
}
