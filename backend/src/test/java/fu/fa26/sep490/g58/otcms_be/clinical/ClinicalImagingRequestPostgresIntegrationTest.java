package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType;
import fu.fa26.sep490.g58.otcms_be.clinical.service.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalImagingRequestService.*;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Real PostgreSQL only. Unique fixture IDs preserve other local test data; no database reset. */
@SpringBootTest
@LocalPostgresTest
class ClinicalImagingRequestPostgresIntegrationTest {
    @MockitoBean VisitClinicalContextPort visits;
    @MockitoBean ClinicServiceCatalogPort catalog;
    // Isolate external owner reads; registration, Clinical persistence and Diagnostic persistence stay real.
    @MockitoBean fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticReadPort diagnosticVisits;
    @MockitoBean fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ImagingServiceCatalogPort imagingCatalog;
    @MockitoBean fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService doctorIdentities;
    @MockitoBean fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountAccessService doctorAccounts;
    @Autowired ClinicalImagingRequestService service;
    @Autowired JdbcTemplate jdbc;
    private final UUID doctorId = UUID.randomUUID(), patientId = UUID.randomUUID(), serviceId = UUID.randomUUID();
    private final UUID visitId = UUID.randomUUID();
    private final UUID doctorAccountId = UUID.randomUUID();
    private final String key = "uc38-" + UUID.randomUUID();

    @BeforeEach void setUp() {
        when(doctorIdentities.findDoctorAccountId(doctorId)).thenReturn(java.util.Optional.of(doctorAccountId));
        when(doctorAccounts.isActiveDoctorAccount(doctorAccountId)).thenReturn(true);
        when(diagnosticVisits.requireImagingOrderEligibility(any(), eq(doctorAccountId))).thenAnswer(call ->
                new fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitEvidence(
                        call.getArgument(0), patientId, doctorAccountId, true, true, true));
        when(imagingCatalog.requireImagingService(eq(serviceId), isNull())).thenReturn(
                new fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ImagingService(serviceId, "MRI", true));
        when(visits.getVisitContext(any())).thenAnswer(invocation ->
                new VisitClinicalContextPort.ClinicalVisitContext(invocation.getArgument(0), patientId, doctorId, true));
        when(catalog.getService(serviceId)).thenReturn(new ClinicServiceCatalogPort.ClinicalServiceSnapshot(
                serviceId, "X-ray Knee", ClinicalServiceType.IMAGING, new BigDecimal("250000.00"), true));
    }

    @Test void concurrentExactRequestsPersistOneRoundAndReplayAndReturnSameRound() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(startTogether(ready, start, () -> service.submit(command(visitId, key))));
            var second = executor.submit(startTogether(ready, start, () -> service.submit(command(visitId, key))));
            releaseWorkers(ready, start);
            var a = first.get(30, TimeUnit.SECONDS); var b = second.get(30, TimeUnit.SECONDS);
            assertEquals(a.round(), b.round()); assertNotEquals(a.replayed(), b.replayed());
        }
        assertCounts(1);
    }

    @Test void duplicateKeyAcrossDifferentVisitsRollsBackLosingRoundAndOrders() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        UUID otherVisit = UUID.randomUUID();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(startTogether(ready, start, () -> outcome(command(visitId, key))));
            var second = executor.submit(startTogether(ready, start, () -> outcome(command(otherVisit, key))));
            releaseWorkers(ready, start);
            var results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(value -> value instanceof SubmissionResult).count());
            assertEquals(1, results.stream().filter(value -> "IDEMPOTENCY_CONFLICT".equals(value)).count());
        }
        assertCounts(1);
    }

    @Test void differentKeysForSameVisitAllocateDistinctRoundNumbers() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(startTogether(ready, start, () -> service.submit(command(visitId, key))));
            var second = executor.submit(startTogether(ready, start, () -> service.submit(command(visitId, key + "-2"))));
            releaseWorkers(ready, start);
            var a = first.get(30, TimeUnit.SECONDS); var b = second.get(30, TimeUnit.SECONDS);
            assertNotEquals(a.round().id(), b.round().id());
            assertEquals(java.util.Set.of(1, 2), java.util.Set.of(a.round().roundNumber(), b.round().roundNumber()));
        }
        assertCounts(2);
    }

    @Test void migrationMovesSubmitKeyToSharedIdempotencyTable() {
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from pg_indexes where schemaname = current_schema()
                and tablename = 'service_orders'
                and indexname in ('uk_clinical_orders_round_head', 'uk_clinical_orders_round_number')
                """, Integer.class));
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from pg_indexes where schemaname = current_schema()
                and tablename = 'service_order_rounds' and indexname = 'uk_clinical_round_visit_number'
                """, Integer.class));
        assertEquals(1, jdbc.queryForObject("""
                select count(*) from pg_indexes where schemaname = current_schema()
                and tablename = 'idempotency_records' and indexname = 'uq_idempotency_records_scope_owner_key'
                """, Integer.class));
        assertEquals(64, jdbc.queryForObject("""
                select character_maximum_length from information_schema.columns where table_schema = current_schema()
                and table_name = 'idempotency_records' and column_name = 'request_fingerprint'
                """, Integer.class));
    }

    @Test void duplicateSubmitKeyIsRejectedByNamedDatabaseConstraint() {
        var submitted = service.submit(command(visitId, key));
        String fingerprint = jdbc.queryForObject("""
                select request_fingerprint from idempotency_records
                where scope = 'ORDER_ROUND_SUBMIT' and owner_id = ? and idempotency_key = ?
                """, String.class, doctorId, key);

        DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                insert into idempotency_records (id, scope, owner_id, idempotency_key, request_fingerprint, resource_id, created_at)
                values (?, 'ORDER_ROUND_SUBMIT', ?, ?, ?, ?, now())
                """, UUID.randomUUID(), doctorId, key, fingerprint, submitted.round().id()));

        org.postgresql.util.PSQLException postgresFailure = findCause(failure,
                org.postgresql.util.PSQLException.class);
        assertNotNull(postgresFailure);
        assertEquals("23505", postgresFailure.getSQLState());
        assertEquals("uq_idempotency_records_scope_owner_key",
                postgresFailure.getServerErrorMessage().getConstraint());
    }

    private static <T extends Throwable> T findCause(Throwable failure, Class<T> type) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) return type.cast(cause);
        }
        return null;
    }

    private Object outcome(ImagingRequestCommand command) {
        try { return service.submit(command); }
        catch (ClinicalOperationException failure) { return failure.code(); }
    }
    /** Coordinate callers before entering the service: the global guard intentionally serializes transactions. */
    private <T> Callable<T> startTogether(CountDownLatch ready, CountDownLatch start, Callable<T> action) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new TimeoutException("workers did not start together");
            }
            return action.call();
        };
    }

    private void releaseWorkers(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        try {
            assertTrue(ready.await(10, TimeUnit.SECONDS), "both workers must be ready before starting");
        } finally {
            start.countDown();
        }
    }
    private ImagingRequestCommand command(UUID visit, String requestKey) {
        return new ImagingRequestCommand(visit, doctorId, requestKey,
                List.of(new ImagingOrderRequest(serviceId, "Reason", "RIGHT_KNEE", null)), true);
    }
    private void assertCounts(int count) {
        assertEquals(0, jdbc.queryForObject("select count(*) from imaging_executions e join service_orders o "
                + "on o.id = e.service_order_id where o.requesting_doctor_id = ?",
                Integer.class, doctorId));
        assertEquals(count, jdbc.queryForObject("select count(*) from idempotency_records where scope = 'ORDER_ROUND_SUBMIT' and owner_id = ?",
                Integer.class, doctorId));
        assertEquals(count, jdbc.queryForObject("select count(*) from service_order_rounds where ordered_by_doctor_id = ?",
                Integer.class, doctorId));
        assertEquals(count, jdbc.queryForObject("select count(*) from service_orders where requesting_doctor_id = ?",
                Integer.class, doctorId));
    }

    @Test void diagnosticAvailabilityDoesNotAffectDoctorSubmission() {
        when(imagingCatalog.requireImagingService(eq(serviceId), isNull())).thenThrow(
                new fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException(
                        fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome.SERVICE_NOT_ELIGIBLE));
        service.submit(command(visitId, key));
        assertCounts(1);
    }
}
