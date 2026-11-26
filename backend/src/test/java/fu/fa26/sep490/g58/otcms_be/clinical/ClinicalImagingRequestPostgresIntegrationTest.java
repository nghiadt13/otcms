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
    @Autowired ClinicalLaboratoryRequestService laboratoryService;
    @Autowired fu.fa26.sep490.g58.otcms_be.clinical.integration.billing.ClinicalRoundBillingAdapter billing;
    @Autowired fu.fa26.sep490.g58.otcms_be.diagnostic.repository.LaboratorySampleRepository samples;
    @Autowired ClinicalLaboratoryDispatchService laboratoryDispatch;
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

    @Test void labRoundReplaysAndUsesTheExistingRoundBillingAdapter() {
        when(catalog.getService(serviceId)).thenReturn(new ClinicServiceCatalogPort.ClinicalServiceSnapshot(
                serviceId, "CBC", ClinicalServiceType.LABORATORY, new BigDecimal("120000.00"), true));
        var command = new ClinicalLaboratoryRequestService.SubmitRequest(visitId, doctorId, key, true,
                List.of(new ClinicalLaboratoryRequestService.OrderRequest(serviceId, "Clinical reason", null)));
        var created = laboratoryService.submit(command);
        var replayed = laboratoryService.submit(command);
        assertFalse(created.replayed());
        assertTrue(replayed.replayed());
        assertEquals(created.round(), replayed.round());
        assertEquals(new BigDecimal("120000.00"), billing.requirePayableRound(created.round().id()).orders().getFirst().priceSnapshot());
        billing.markRoundPaid(created.round().id());
        var persisted = laboratoryService.submit(command).round().orders().getFirst();
        assertEquals(fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderPaymentStatus.PAID, persisted.paymentStatus());
        var conflicting = new ClinicalLaboratoryRequestService.SubmitRequest(visitId, doctorId, key, true,
                List.of(new ClinicalLaboratoryRequestService.OrderRequest(serviceId, "Changed reason", null)));
        assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(ClinicalOperationException.class,
                () -> laboratoryService.submit(conflicting)).code());
        assertCounts(1);
    }

    @Test void concurrentLabSubmissionsCreateOnlyOneRound() throws Exception {
        when(catalog.getService(serviceId)).thenReturn(new ClinicServiceCatalogPort.ClinicalServiceSnapshot(
                serviceId, "CBC", ClinicalServiceType.LABORATORY, new BigDecimal("120000.00"), true));
        var command = new ClinicalLaboratoryRequestService.SubmitRequest(visitId, doctorId, key, true,
                List.of(new ClinicalLaboratoryRequestService.OrderRequest(serviceId, "Clinical reason", null)));
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(startTogether(ready, start, () -> laboratoryService.submit(command)));
            var second = executor.submit(startTogether(ready, start, () -> laboratoryService.submit(command)));
            releaseWorkers(ready, start);
            var a = first.get(30, TimeUnit.SECONDS);
            var b = second.get(30, TimeUnit.SECONDS);
            assertEquals(a.round(), b.round());
            assertNotEquals(a.replayed(), b.replayed());
        }
        assertCounts(1);
    }

    @Test @org.springframework.transaction.annotation.Transactional
    void laboratorySamplesPersistSeparateCollectionAttemptsWithUniqueCodes() {
        when(catalog.getService(serviceId)).thenReturn(new ClinicServiceCatalogPort.ClinicalServiceSnapshot(
                serviceId, "CBC", ClinicalServiceType.LABORATORY, new BigDecimal("120000.00"), true));
        var command = new ClinicalLaboratoryRequestService.SubmitRequest(visitId, doctorId, key, true,
                List.of(new ClinicalLaboratoryRequestService.OrderRequest(serviceId, "Reason", null)));
        var orderId = laboratoryService.submit(command).round().orders().getFirst().orderId();
        UUID actor = UUID.randomUUID();
        jdbc.update("insert into accounts(id, normalized_email, account_type, status, created_at, updated_at) values (?, ?, 'LAB_TECHNICIAN', 'ACTIVE', now(), now())",
                actor, actor + "@lab-sample.test");
        var now = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var first = new fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.LaboratorySampleJpaEntity(
                UUID.randomUUID(), orderId, 1, "sample-" + UUID.randomUUID(), actor, now, actor, now);
        first.reject("Unsuitable", "Partner", actor, now);
        samples.saveAndFlush(first);
        var second = new fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.LaboratorySampleJpaEntity(
                UUID.randomUUID(), orderId, 2, "sample-" + UUID.randomUUID(), actor, now, actor, now);
        samples.saveAndFlush(second);
        var attempts = samples.findAttempts(orderId);
        assertEquals(List.of(2, 1), attempts.stream().map(item -> item.getAttemptNumber()).toList());
        assertEquals("Partner", attempts.getLast().getRejectionSource());
        assertEquals("Unsuitable", attempts.getLast().getRejectionReason());
    }

    @Test @org.springframework.transaction.annotation.Transactional
    void paidLaboratoryOrderIsAssignedWithRealRoomAndAuditPersistence() {
        when(catalog.getService(serviceId)).thenReturn(new ClinicServiceCatalogPort.ClinicalServiceSnapshot(
                serviceId, "CBC", ClinicalServiceType.LABORATORY, new BigDecimal("120000.00"), true));
        var command = new ClinicalLaboratoryRequestService.SubmitRequest(visitId, doctorId, key, true,
                List.of(new ClinicalLaboratoryRequestService.OrderRequest(serviceId, "Reason", null)));
        var round = laboratoryService.submit(command).round();
        var orderId = round.orders().getFirst().orderId();
        UUID room = UUID.randomUUID(), actor = UUID.randomUUID();
        jdbc.update("insert into clinic_services(id,name,service_type,duration_minutes,buffer_minutes,price) values (?,?,'LABORATORY',15,0,100)", serviceId, "Lab " + serviceId);
        jdbc.update("insert into rooms(id,room_code,display_label,room_type,capabilities) values (?,?,'Lab room','LABORATORY',array['LAB_ROOM'])", room, "LAB-" + room);
        jdbc.update("insert into room_supported_services(room_id,service_id) values (?,?)", room, serviceId);
        jdbc.update("insert into accounts(id,normalized_email,account_type,status,created_at,updated_at) values (?,?,'RECEPTIONIST','ACTIVE',now(),now())", actor, actor + "@lab-dispatch.test");
        billing.markRoundPaid(round.id());
        var receptionist = new fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount(
                actor, fu.fa26.sep490.g58.otcms_be.common.security.AccountRole.RECEPTIONIST, UUID.randomUUID());
        laboratoryDispatch.assign(orderId, room, receptionist);
        laboratoryDispatch.assign(orderId, room, receptionist);
        assertThrows(ClinicalOperationException.class, () -> laboratoryDispatch.assign(orderId, UUID.randomUUID(), receptionist));
        assertEquals(room, jdbc.queryForObject("select room_id from clinical_service_orders where id=?", UUID.class, orderId));
        assertEquals("ASSIGNED", jdbc.queryForObject("select lab_status from clinical_service_orders where id=?", String.class, orderId));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_logs where entity_id=? and action='ASSIGN_ROOM'", Integer.class, orderId));
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
