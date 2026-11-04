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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
        synchronizeFirstTwoVisitReads();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.submit(command(visitId, key)));
            var second = executor.submit(() -> service.submit(command(visitId, key)));
            var a = first.get(30, TimeUnit.SECONDS); var b = second.get(30, TimeUnit.SECONDS);
            assertEquals(a.round(), b.round()); assertNotEquals(a.replayed(), b.replayed());
        }
        assertCounts(1);
    }

    @Test void duplicateKeyAcrossDifferentVisitsRollsBackLosingRoundAndOrders() throws Exception {
        synchronizeFirstTwoVisitReads();
        UUID otherVisit = UUID.randomUUID();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> outcome(command(visitId, key)));
            var second = executor.submit(() -> outcome(command(otherVisit, key)));
            var results = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(value -> value instanceof SubmissionResult).count());
            assertEquals(1, results.stream().filter(value -> "IDEMPOTENCY_CONFLICT".equals(value)).count());
        }
        assertCounts(1);
    }

    @Test void differentKeysForSameVisitAllocateDistinctRoundNumbers() throws Exception {
        synchronizeFirstTwoVisitReads();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.submit(command(visitId, key)));
            var second = executor.submit(() -> service.submit(command(visitId, key + "-2")));
            var a = first.get(30, TimeUnit.SECONDS); var b = second.get(30, TimeUnit.SECONDS);
            assertNotEquals(a.round().id(), b.round().id());
            assertEquals(java.util.Set.of(1, 2), java.util.Set.of(a.round().roundNumber(), b.round().roundNumber()));
        }
        assertCounts(2);
    }

    @Test void migrationProvidesNamedUniqueConstraintsFingerprintLengthAndForeignKey() {
        assertEquals(3, jdbc.queryForObject("""
                select count(*) from pg_constraint where conrelid = 'clinical_imaging_request_replays'::regclass
                and conname in ('uk_clinical_imaging_replay_doctor_key', 'uk_clinical_imaging_replay_round',
                                'fk_clinical_imaging_replay_round')
                """, Integer.class));
        assertEquals(64, jdbc.queryForObject("""
                select character_maximum_length from information_schema.columns where table_schema = current_schema()
                and table_name = 'clinical_imaging_request_replays' and column_name = 'request_fingerprint'
                """, Integer.class));
    }

    private Object outcome(ImagingRequestCommand command) {
        try { return service.submit(command); }
        catch (ClinicalOperationException failure) { return failure.code(); }
    }
    private void synchronizeFirstTwoVisitReads() {
        var barrier = new CyclicBarrier(2); var calls = new AtomicInteger();
        when(visits.getVisitContext(any())).thenAnswer(invocation -> {
            if (calls.getAndIncrement() < 2) barrier.await(10, TimeUnit.SECONDS);
            return new VisitClinicalContextPort.ClinicalVisitContext(invocation.getArgument(0), patientId, doctorId, true);
        });
    }
    private ImagingRequestCommand command(UUID visit, String requestKey) {
        return new ImagingRequestCommand(visit, doctorId, requestKey,
                List.of(new ImagingOrderRequest(serviceId, "Reason", "RIGHT_KNEE", null)));
    }
    private void assertCounts(int count) {
        assertEquals(count, jdbc.queryForObject("select count(*) from diagnostic_requests where ordering_doctor_id = ? and service_order_id is not null",
                Integer.class, doctorAccountId));
        assertEquals(0, jdbc.queryForObject("select count(*) from diagnostic_requests where ordering_doctor_id = ? and room_id is not null",
                Integer.class, doctorAccountId));
        assertEquals(count, jdbc.queryForObject("select count(*) from clinical_imaging_request_replays where doctor_profile_id = ?",
                Integer.class, doctorId));
        assertEquals(count, jdbc.queryForObject("select count(*) from clinical_ordering_rounds where ordered_by_doctor_id = ?",
                Integer.class, doctorId));
        assertEquals(count, jdbc.queryForObject("select count(*) from clinical_service_orders where requesting_doctor_id = ?",
                Integer.class, doctorId));
    }

    @Test void diagnosticRegistrationFailureRollsBackRoundOrdersAndReplay() {
        when(imagingCatalog.requireImagingService(eq(serviceId), isNull())).thenThrow(
                new fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException(
                        fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome.SERVICE_NOT_ELIGIBLE));
        assertThrows(fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException.class,
                () -> service.submit(command(visitId, key)));
        assertCounts(0);
    }
}
