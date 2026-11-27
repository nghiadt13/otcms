package fu.fa26.sep490.g58.otcms_be.common.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fu.fa26.sep490.g58.otcms_be.common.exception.CoordinationGuardUnavailableException;
import fu.fa26.sep490.g58.otcms_be.common.exception.ResourceMutationBlockedException;
import fu.fa26.sep490.g58.otcms_be.common.model.AdminMutationImpact;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.CannotCreateTransactionException;

class CoordinationFailureExceptionHandlerTest {
    private final CoordinationFailureExceptionHandler handler = new CoordinationFailureExceptionHandler();

    private static MockHttpServletRequest requestWithCorrelation(String correlationId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(CorrelationIdFilter.ATTRIBUTE, correlationId);
        return request;
    }

    @Test
    void resourceMutationBlockedReturns409WithImpactAndCorrelationId() {
        UUID resourceId = UUID.randomUUID();
        var impact = new AdminMutationImpact("CLINIC_SERVICE", resourceId, true, 3L, true);
        var ex = new ResourceMutationBlockedException(impact);

        ResponseEntity<ErrorResponse> response = handler.resourceMutationBlocked(ex, requestWithCorrelation("corr-409"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("RESOURCE_MUTATION_BLOCKED", response.getBody().error().code());
        assertEquals("corr-409", response.getBody().error().correlationId());
        assertSame(impact, response.getBody().error().impact());
    }

    @Test
    void guardUnavailableReturns503DependencyUnavailable() {
        var ex = new CoordinationGuardUnavailableException();

        ResponseEntity<ErrorResponse> withRequest = handler.guardUnavailable(ex, requestWithCorrelation("corr-503"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, withRequest.getStatusCode());
        assertEquals("DEPENDENCY_UNAVAILABLE", withRequest.getBody().error().code());
        assertEquals("corr-503", withRequest.getBody().error().correlationId());

        ResponseEntity<ErrorResponse> withoutRequest = handler.guardUnavailable(ex);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, withoutRequest.getStatusCode());
        assertEquals("DEPENDENCY_UNAVAILABLE", withoutRequest.getBody().error().code());
        assertNotNull(withoutRequest.getBody().error().correlationId());
    }

    @ParameterizedTest
    @CsvSource({
            "23P01,409,SLOT_UNAVAILABLE",
            "55P03,503,DEPENDENCY_UNAVAILABLE",
            "57014,503,DEPENDENCY_UNAVAILABLE",
            "40P01,503,DEPENDENCY_UNAVAILABLE"
    })
    void databaseFailureMapsKnownPostgresSqlStates(String sqlState, int status, String code) {
        var sqlEx = new SQLException("pg error", sqlState);
        var wrapped = new DataAccessResourceFailureException("db failed", sqlEx);

        ResponseEntity<ErrorResponse> response = handler.databaseFailure(wrapped, requestWithCorrelation("corr-db"));

        assertEquals(status, response.getStatusCode().value());
        assertEquals(code, response.getBody().error().code());
        assertEquals("corr-db", response.getBody().error().correlationId());
    }

    @Test
    void databaseFailureRethrowsUnmappedSqlState() {
        var unmapped = new DataIntegrityViolationException("unique violation", new SQLException("dup", "23505"));

        var thrown = assertThrows(DataIntegrityViolationException.class,
                () -> handler.databaseFailure(unmapped));
        assertSame(unmapped, thrown);
    }

    @Test
    void databaseFailureHandlesCircularCauseAndNextExceptionChainsSafely() {
        SQLException sqlA = new SQLException("first", "08000");
        SQLException sqlB = new SQLException("second", "08001") {
            @Override
            public SQLException getNextException() {
                return sqlA;
            }
        };
        sqlA.setNextException(sqlB);
        var txFailure = new CannotCreateTransactionException("tx cycle", sqlA);

        var rethrown = assertThrows(CannotCreateTransactionException.class,
                () -> handler.databaseFailure(txFailure, requestWithCorrelation("corr-cycle")));
        assertSame(txFailure, rethrown);

        RuntimeException causeCycle = new RuntimeException("a");
        RuntimeException causeB = new RuntimeException("b", causeCycle);
        causeCycle.initCause(causeB);
        var wrappedCycle = new DataAccessResourceFailureException("cause cycle", causeCycle);
        assertSame(wrappedCycle, assertThrows(DataAccessResourceFailureException.class,
                () -> handler.databaseFailure(wrappedCycle, requestWithCorrelation("corr-cycle2"))));

        SQLException mapped = new SQLException("deadlock", "40P01");
        SQLException chainWithMapped = new SQLException("outer", "08000");
        chainWithMapped.setNextException(mapped);
        ResponseEntity<ErrorResponse> resolved = handler.databaseFailure(
                new CannotCreateTransactionException("tx ok", chainWithMapped),
                requestWithCorrelation("corr-ok"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, resolved.getStatusCode());
        assertEquals("DEPENDENCY_UNAVAILABLE", resolved.getBody().error().code());
        assertTrue(resolved.getBody().error().message().contains("unavailable"));
    }
}
