package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ClinicalImagingOrderPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ClinicalImagingOrder;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ImagingService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.RoomEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.RoomReservation;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StudyEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.StudyVerificationStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.UseEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.UseEvidenceReceipt;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.FindingsReleaseEvent;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ReleaseAcknowledgement;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.FirstUseEvidencePort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ImagingBillingPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ImagingStudyPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ImagingServiceCatalogPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.RoomReservationPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequestPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequestView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.CancellationCause;
import fu.fa26.sep490.g58.otcms_be.diagnostic.mapper.DiagnosticDtoMapper;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@LocalPostgresTest
@Import(DiagnosticRequestT012PostgresIntegrationTest.PortConfiguration.class)
class DiagnosticRequestT012PostgresIntegrationTest {
    private final Set<UUID> visitIds = new HashSet<>();
    private UUID visitId;
    private UUID patientId;
    private UUID doctorId;
    private UUID serviceId;
    private UUID roomId;
    private UUID alternateRoomId;
    private UUID technicianId;

    @Autowired DiagnosticRequestService service;
    @Autowired DiagnosticImagingRequestRegistration registration;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    private final Map<String, UUID> fixtureOrders = new HashMap<>();
    @Autowired DiagnosticDtoMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PortFakes ports;

    @BeforeEach
    void setUp() {
        ports.reset(); fixtureOrders.clear();
        visitId = UUID.randomUUID();
        patientId = UUID.randomUUID();
        doctorId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        roomId = UUID.randomUUID();
        alternateRoomId = UUID.randomUUID();
        technicianId = UUID.randomUUID();
        visitIds.add(visitId);
        ports.visits.put(visitId, new VisitEvidence(visitId, patientId, doctorId, true, true, true));
        ports.service = new ImagingService(serviceId, "MRI", true);
        ports.addRoom(roomId, "MRI", technicianId, 7);
        ports.addRoom(alternateRoomId, "MRI", technicianId, 9);
    }

    @AfterEach
    void cleanRows() {
        for (UUID id : visitIds) {
            jdbc.update("delete from diagnostic_audit_events where visit_id = ?", id);
            jdbc.update("delete from diagnostic_outbox where visit_id = ?", id);
            jdbc.update("delete from diagnostic_idempotency_records where visit_id = ?", id);
            jdbc.update("delete from diagnostic_requests where visit_id = ?", id);
        }
    }

    @Test
    void createsWithOwnerEvidenceReservesRoomAndReplaysTheSameIdempotentResult() {
        DiagnosticRequestView first = create("create-1", "Chest", "Pain and shortness of breath");
        DiagnosticRequestView replay = create("create-1", "Chest", "Pain and shortness of breath");

        assertEquals(first.requestId(), replay.requestId());
        assertEquals(DiagnosticRequestStatus.PENDING, replay.status());
        assertEquals(0, replay.version());
        assertEquals("Pain and shortness of breath", replay.clinicalReason());
        assertEquals(1, ports.reservationCalls);
        assertEquals(1, jdbc.queryForObject("select count(*) from diagnostic_requests where visit_id = ?",
                Integer.class, visitId));
        assertEquals(0, jdbc.queryForObject("select count(*) from diagnostic_idempotency_records "
                + "where visit_id = ? and operation = 'CREATE_REQUEST'", Integer.class, visitId));

        DiagnosticOperationException conflict = assertThrows(DiagnosticOperationException.class,
                () -> create("create-1", "Chest", "Different reason"));
        assertEquals(DiagnosticOperationOutcome.EVIDENCE_MISMATCH, conflict.outcome());
    }

    @Test
    void directDoctorCreationIsRetiredRegardlessOfRoomCapacity() {
        for (boolean capacity : List.of(false, true)) {
            ports.rooms.get(roomId).hasCapacity = capacity;
            var error = assertThrows(DiagnosticOperationException.class, () -> service.create(doctor(), visitId,
                    "retired", serviceId, "MRI", "Chest", "Pain", roomId, null, "corr"));
            assertEquals(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD, error.outcome());
        }
        assertEquals(0, jdbc.queryForObject("select count(*) from diagnostic_requests where visit_id = ?", Integer.class, visitId));
        assertEquals(0, jdbc.queryForObject("select count(*) from diagnostic_idempotency_records where visit_id = ?", Integer.class, visitId));
    }

    @Test
    void doctorCanCancelBeforeUseAndRoomReservationIsReleasedIdempotently() {
        DiagnosticRequestView created = create("create-cancel", "Chest", "Pain");
        DiagnosticRequestView cancelled = service.cancel(doctor(), created.requestId(), "cancel-1", 0,
                CancellationCause.PATIENT_DECLINED, "Patient declined", "corr-cancel");
        DiagnosticRequestView replay = service.cancel(doctor(), created.requestId(), "cancel-1", 0,
                CancellationCause.PATIENT_DECLINED, "Patient declined", "corr-cancel");

        assertEquals(DiagnosticRequestStatus.CANCELLED, cancelled.status());
        assertEquals(1, cancelled.version());
        assertEquals(cancelled, replay);
        assertEquals(1, ports.releaseCalls);
        assertEquals("PATIENT_DECLINED", cancelled.cancellationCause());
        assertEquals(1, jdbc.queryForObject("select count(*) from diagnostic_audit_events "
                + "where request_id = ? and action = 'MANUAL_CANCEL'", Integer.class, created.requestId()));
    }

    @Test
    void roomTransferUsesNewRoomAndRetryDoesNotRepeatReservationChanges() {
        DiagnosticRequestView created = create("create-transfer", "Chest", "Pain");
        DiagnosticRequestView transferred = service.transferRoom(doctor(), created.requestId(), "transfer-1", 0,
                alternateRoomId, "Machine service", "corr-transfer");
        DiagnosticRequestView replay = service.transferRoom(doctor(), created.requestId(), "transfer-1", 0,
                alternateRoomId, "Machine service", "corr-transfer");

        assertEquals(alternateRoomId, transferred.roomId());
        assertEquals(1, transferred.version());
        assertEquals(transferred, replay);
        assertEquals(2, ports.reservationCalls);
        assertEquals(1, ports.releaseCalls);
        assertEquals(alternateRoomId, ports.reservations.get(created.requestId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from diagnostic_audit_events "
                + "where request_id = ? and action = 'TRANSFER_ROOM'", Integer.class, created.requestId()));

        DiagnosticOperationException stale = assertThrows(DiagnosticOperationException.class,
                () -> service.transferRoom(doctor(), created.requestId(), "transfer-stale", 0,
                        roomId, "Stale update", "corr-stale"));
        assertEquals(DiagnosticOperationOutcome.VERSION_CONFLICT, stale.outcome());
    }

    @Test
    void assignedWorklistIsRoomScopedAndDoesNotExposeClinicalDetails() {
        DiagnosticRequestView created = create("create-work", "Chest", "Sensitive reason");
        DiagnosticRequestPage page = service.listAssignedUploadWork(
                DiagnosticActor.user(technicianId, AccountRole.IMAGING_TECHNICIAN), roomId, 0, 20);

        assertEquals(1, page.totalItems());
        assertEquals(created.requestId(), page.items().getFirst().requestId());
        assertEquals(DiagnosticRequestStatus.PENDING, page.items().getFirst().status());
        var uploadItem = mapper.toAssignedUploadWorkItem(page.items().getFirst());
        assertFalse(Arrays.stream(uploadItem.getClass().getRecordComponents())
                .anyMatch(component -> component.getName().equals("clinicalReason")
                        || component.getName().equals("currentStudyRef")));
    }

    @Test
    void wrongDoctorCannotCreateOrMutateAnotherDoctorsRequest() {
        DiagnosticActor wrongDoctor = DiagnosticActor.user(UUID.randomUUID(), AccountRole.DOCTOR);
        DiagnosticOperationException deniedCreate = assertThrows(DiagnosticOperationException.class,
                () -> service.create(wrongDoctor, visitId, "wrong-doctor-create", serviceId, "MRI", "Chest",
                        "Pain", roomId, null, "corr-wrong"));
        assertEquals(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD, deniedCreate.outcome());
        assertEquals(0, jdbc.queryForObject("select count(*) from diagnostic_requests where visit_id = ?",
                Integer.class, visitId));
    }

    @Test
    void linksTheRequestToAnOpenClinicalOrderOfTheSameVisitAndService() {
        UUID orderId = UUID.randomUUID();
        ports.orders.put(orderId, new ClinicalImagingOrder(orderId, visitId, serviceId, true, true));

        DiagnosticRequestView created = createLinked("link-ok", orderId);

        assertEquals(orderId, jdbc.queryForObject("select service_order_id from diagnostic_requests where id = ?",
                UUID.class, created.requestId()));
    }

    @Test
    void rejectsAnOrderThatIsMissingClosedOrOfAnotherVisitOrService() {
        UUID missing = UUID.randomUUID();
        UUID closed = UUID.randomUUID();
        UUID otherVisit = UUID.randomUUID();
        UUID otherService = UUID.randomUUID();
        ports.orders.put(closed, new ClinicalImagingOrder(closed, visitId, serviceId, false, true));
        ports.orders.put(otherVisit, new ClinicalImagingOrder(otherVisit, UUID.randomUUID(), serviceId, true, true));
        ports.orders.put(otherService, new ClinicalImagingOrder(otherService, visitId, UUID.randomUUID(), true, true));

        assertEquals(DiagnosticOperationOutcome.EVIDENCE_MISMATCH,
                assertThrows(DiagnosticOperationException.class, () -> createLinked("link-missing", missing)).outcome());
        for (UUID bad : List.of(closed, otherVisit, otherService)) {
            assertEquals(DiagnosticOperationOutcome.EVIDENCE_MISMATCH,
                    assertThrows(DiagnosticOperationException.class, () -> createLinked("link-bad-" + bad, bad)).outcome());
        }
        assertEquals(0, jdbc.queryForObject("select count(*) from diagnostic_requests where visit_id = ?",
                Integer.class, visitId));
    }

    @Test
    void anOrderHasOneLiveRequestAndCancellingItFreesTheOrderAndCancelsTheClinicalOrder() {
        UUID orderId = UUID.randomUUID();
        ports.orders.put(orderId, new ClinicalImagingOrder(orderId, visitId, serviceId, true, true));
        DiagnosticRequestView first = createLinked("link-first", orderId);

        assertEquals(first.requestId(), createLinked("link-second", orderId).requestId());

        service.cancel(doctor(), first.requestId(), "link-cancel", 0,
                CancellationCause.PATIENT_DECLINED, "Patient declined", "corr-cancel");
        assertEquals(List.of(orderId), ports.orderCancelCalls);
        assertThrows(DiagnosticOperationException.class, () -> createLinked("link-third", orderId));
        assertEquals(0, jdbc.queryForObject("select count(*) from diagnostic_requests where service_order_id=? and status <> 'CANCELLED'", Integer.class, orderId));
    }

    @Test
    void anUnlinkedDirectRequestIsDeniedWithoutClinicalMutation() {
        assertEquals(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD, assertThrows(DiagnosticOperationException.class,
                () -> service.create(doctor(), visitId, "unlinked", serviceId, "MRI", "Chest", "Pain", roomId, null, "corr")).outcome());
        assertEquals(List.of(), ports.orderCancelCalls); assertEquals(List.of(), ports.orderResultCalls);
    }

    private DiagnosticRequestView createLinked(String key, UUID orderId) {
        return registerAssigned(jdbc, transactions, registration, ports, visitId, roomId, orderId, "Chest", "Pain");
    }
    private DiagnosticRequestView create(String key, String bodyArea, String reason) {
        UUID order = fixtureOrders.computeIfAbsent(key, ignored -> UUID.randomUUID());
        ports.orders.put(order, new ClinicalImagingOrder(order, visitId, serviceId, true, true));
        return registerAssigned(jdbc, transactions, registration, ports, visitId, roomId, order, bodyArea, reason);
    }

    /** Lifecycle fixture: real paid queue registration plus explicit TEST physical reservation evidence. */
    static DiagnosticRequestView registerAssigned(JdbcTemplate jdbc, org.springframework.transaction.PlatformTransactionManager transactions,
            DiagnosticImagingRequestRegistration registration, PortFakes ports, UUID visitId, UUID roomId,
            UUID orderId, String area, String reason) {
        var visit = ports.visits.get(visitId);
        UUID id = new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status ->
                registration.register(new DiagnosticImagingRequestRegistration.RegisterImagingOrder(orderId, visitId,
                        visit.patientId(), visit.treatingDoctorId(), ports.service.serviceId(), roomId, area, reason)).requestId());
        // Explicit physical-reservation fixture for legacy lifecycle assertions only; dispatch never calls this.
        if (!ports.reservations.containsKey(id)) { ports.reservationCalls++; ports.reservations.put(id, roomId); }
        return jdbc.queryForObject("select * from diagnostic_requests where id = ?", (row, number) -> new DiagnosticRequestView(
                id, visitId, visit.patientId(), visit.treatingDoctorId(), ports.service.serviceId(), roomId,
                ports.service.imagingType(), row.getString("body_area"), row.getString("clinical_reason"),
                DiagnosticRequestStatus.valueOf(row.getString("status")), row.getString("cancellation_cause"),
                row.getString("cancellation_reason"), null, row.getLong("version"), row.getTimestamp("created_at").toInstant()), id);
    }

    private DiagnosticActor doctor() { return DiagnosticActor.user(doctorId, AccountRole.DOCTOR); }

    @TestConfiguration(proxyBeanMethods = false)
    static class PortConfiguration {
        @Bean @Primary fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService registrationQueueRooms() {
            return org.mockito.Mockito.mock(fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService.class);
        }
        @Bean PortFakes diagnosticRequestPortFakes() { return new PortFakes(); }
        // These Diagnostic persistence tests intentionally use their explicit Visit fixture.
        // Real Visit/Diagnostic composition is verified by DoctorImagingReviewIntegrationTest.
        @Bean @Primary fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService registrationIdentities() {
            return new fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService() {
                public java.util.Map<UUID, String> findDoctorNames(java.util.Collection<UUID> ids) { return java.util.Map.of(); }
                public java.util.Optional<UUID> findDoctorProfileId(UUID id) { return java.util.Optional.ofNullable(id); }
                public java.util.Optional<UUID> findDoctorAccountId(UUID id) { return java.util.Optional.ofNullable(id); }
            };
        }
        @Bean @Primary fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountAccessService registrationDoctorAccounts() { return id -> true; }
        @Bean @Primary VisitDiagnosticPort visitDiagnosticPort(PortFakes fakes) { return new FakeVisitPort(fakes); }
        @Bean RoomReservationPort roomReservationPort(PortFakes fakes) { return new FakeRoomPort(fakes); }
        // These lifecycle fixtures use PortFakes.service; keep the production adapter available but select this test port.
        @Bean @Primary ImagingServiceCatalogPort imagingServiceCatalogPort(PortFakes fakes) { return new FakeCatalogPort(fakes); }
        @Bean FirstUseEvidencePort firstUseEvidencePort(PortFakes fakes) { return new FakeFirstUsePort(fakes); }
        @Bean ImagingStudyPort imagingStudyPort(PortFakes fakes) { return new FakeStudyPort(fakes); }
        @Bean ImagingBillingPort imagingBillingPort(PortFakes fakes) { return new FakeBillingPort(fakes); }
        /** Replaces the production adapter so link tests do not need real clinical orders. */
        @Bean @Primary ClinicalImagingOrderPort clinicalImagingOrderPort(PortFakes fakes) {
            return new FakeOrderPort(fakes);
        }
    }

    static class FakeOrderPort implements ClinicalImagingOrderPort {
        private final PortFakes state;
        FakeOrderPort(PortFakes state) { this.state = state; }
        @Override public ClinicalImagingOrder requireOpenImagingOrder(UUID orderId) { return state.orders.get(orderId); }
        @Override public void markResultAvailable(UUID orderId) { state.orderResultCalls.add(orderId); }
        @Override public void cancelOrderIfOpen(UUID orderId, String reason) {
            state.orderCancelCalls.add(orderId);
            var current = state.orders.get(orderId);
            if (current != null) state.orders.put(orderId, new ClinicalImagingOrder(orderId, current.visitId(), current.serviceId(), false, current.paid()));
        }
    }

    static class PortFakes {
        final Map<UUID, ClinicalImagingOrder> orders = new HashMap<>();
        final List<UUID> orderResultCalls = new ArrayList<>();
        final List<UUID> orderCancelCalls = new ArrayList<>();
        final Map<UUID, VisitEvidence> visits = new HashMap<>();
        final Map<UUID, MutableRoom> rooms = new HashMap<>();
        final Map<UUID, UUID> reservations = new HashMap<>();
        ImagingService service;
        boolean reservationAllowed = true;
        int reservationCalls;
        int releaseCalls;
        int firstUseCalls;
        int studyCalls;
        int billingCalls;
        int resultReadyCalls;
        boolean firstUseAccepted = true;
        boolean billingFails;
        boolean resultReadyFails;
        boolean findingsReleaseFails;
        java.util.function.Consumer<FindingsReleaseEvent> beforeStudyReleaseAck = event -> {};
        int findingsReleaseCalls;
        int studyReleaseCalls;
        StudyEvidence studyConfirmation;
        final List<UUID> billedRequestIds = new ArrayList<>();

        void addRoom(UUID id, String type, UUID technicianId, long version) {
            rooms.put(id, new MutableRoom(new RoomEvidence(id, type, true, true, true, technicianId, version)));
        }

        void reset() {
            visits.clear(); rooms.clear(); reservations.clear(); service = null;
            reservationAllowed = true; reservationCalls = 0; releaseCalls = 0;
            firstUseCalls = 0; studyCalls = 0; billingCalls = 0; resultReadyCalls = 0;
            firstUseAccepted = true; billingFails = false; resultReadyFails = false;
            findingsReleaseFails = false; findingsReleaseCalls = 0; studyReleaseCalls = 0;
            beforeStudyReleaseAck = event -> {};
            studyConfirmation = null; billedRequestIds.clear();
            orders.clear(); orderResultCalls.clear(); orderCancelCalls.clear();
        }

    }

    static class FakeVisitPort implements VisitDiagnosticPort {
        private final PortFakes state;
        FakeVisitPort(PortFakes state) { this.state = state; }
        @Override public VisitEvidence requireImagingOrderEligibility(UUID visitId, UUID actorAccountId) {
            return state.visits.get(visitId);
        }
        @Override public fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.NoShowDecision
                acceptDeferredNoShowCancellation(UUID requestId, UUID visitId, java.time.Instant deadline, UUID eventId) {
            throw new UnsupportedOperationException("T012 fake does not implement deferred no-show");
        }
        @Override public void publishResultReady(UUID requestId, UUID visitId, UUID eventId) {
            state.resultReadyCalls++;
            if (state.resultReadyFails) throw new IllegalStateException("Visit unavailable");
        }
        @Override public ReleaseAcknowledgement publishFindingsReleased(FindingsReleaseEvent event) {
            state.findingsReleaseCalls++;
            if (state.findingsReleaseFails) throw new IllegalStateException("Visit unavailable");
            return new ReleaseAcknowledgement(event.eventId(), event.requestId(), releasePayloadSha256(event), true);
        }
        @Override public fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitHistoryAuthorization
                authorizeImagingHistory(UUID visitId, UUID actorId) {
            return null;
        }
        @Override public fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitStatusAuthorization
                authorizeImagingStatus(UUID visitId, UUID actorId,
                        fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.VisitStatusResponsibility responsibility) {
            return null;
        }
    }

    static class FakeRoomPort implements RoomReservationPort {
        private final PortFakes state;
        FakeRoomPort(PortFakes state) { this.state = state; }
        @Override public RoomEvidence requireEligibleImagingRoom(UUID roomId, String imagingType) {
            MutableRoom room = state.rooms.get(roomId);
            return room == null ? null : room.evidence();
        }
        @Override public RoomReservation reserveImagingCapacity(UUID requestId, UUID roomId,
                String imagingType, long expectedRoomVersion) {
            state.reservationCalls++;
            if (!state.reservationAllowed) return new RoomReservation(requestId, roomId, expectedRoomVersion, false);
            state.reservations.put(requestId, roomId);
            return new RoomReservation(requestId, roomId, expectedRoomVersion, true);
        }
        @Override public boolean hasImagingCapacityReservation(UUID requestId) { return state.reservations.containsKey(requestId); }
        @Override public void releaseImagingCapacity(UUID requestId, String cause) {
            state.releaseCalls++;
            state.reservations.remove(requestId);
        }
    }

    static class FakeCatalogPort implements ImagingServiceCatalogPort {
        private final PortFakes state;
        FakeCatalogPort(PortFakes state) { this.state = state; }
        @Override public ImagingService requireImagingService(UUID serviceId, String imagingType) {
            return state.service;
        }
    }

    static class FakeFirstUsePort implements FirstUseEvidencePort {
        private final PortFakes state;
        FakeFirstUsePort(PortFakes state) { this.state = state; }
        @Override public UseEvidenceReceipt recordFirstUseEvidence(UseEvidence evidence) {
            state.firstUseCalls++;
            return new UseEvidenceReceipt(evidence.eventId(), evidence.requestId(), evidence.attemptId(),
                    state.firstUseAccepted, false);
        }
    }

    static class FakeStudyPort implements ImagingStudyPort {
        private final PortFakes state;
        FakeStudyPort(PortFakes state) { this.state = state; }
        @Override public StudyEvidence confirmIngestedStudy(UUID eventId, String studyRef, UUID requestId,
                UUID visitId, UUID patientId, UUID attemptId, java.util.List<String> verificationEvidence) {
            state.studyCalls++;
            return state.studyConfirmation == null
                    ? new StudyEvidence(eventId, studyRef, requestId, visitId, patientId, attemptId,
                            StudyVerificationStatus.CONFIRMED_MATCH, true, verificationEvidence)
                    : state.studyConfirmation;
        }
        @Override public fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ViewAuthorization
                authorizeStudyView(String studyRef, UUID actorId, String purpose) {
            return new fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ViewAuthorization(
                    false, Instant.EPOCH);
        }
        @Override public ReleaseAcknowledgement acceptFindingsRelease(FindingsReleaseEvent event) {
            state.studyReleaseCalls++;
            if (state.findingsReleaseFails) throw new IllegalStateException("FEAT-004 unavailable");
            state.beforeStudyReleaseAck.accept(event);
            return new ReleaseAcknowledgement(event.eventId(), event.requestId(), releasePayloadSha256(event), true);
        }
    }

    static String releasePayloadSha256(FindingsReleaseEvent event) {
        String canonical = String.join("\n", event.eventId().toString(), event.requestId().toString(),
                event.visitId().toString(), event.patientId().toString(), event.studyRef(),
                event.findingsVersionId().toString(), event.reportId().toString(), event.reportSha256());
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static class FakeBillingPort implements ImagingBillingPort {
        private final PortFakes state;
        FakeBillingPort(PortFakes state) { this.state = state; }
        @Override public UUID registerFirstUseObligation(UUID eventId, UUID requestId, UUID serviceId,
                Instant occurredAt) {
            state.billingCalls++;
            if (state.billingFails) throw new IllegalStateException("Billing unavailable");
            state.billedRequestIds.add(requestId);
            return UUID.nameUUIDFromBytes(("billing:" + requestId)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    static class MutableRoom {
        private final RoomEvidence original;
        private boolean hasCapacity = true;
        MutableRoom(RoomEvidence original) { this.original = original; }
        RoomEvidence evidence() {
            return new RoomEvidence(original.roomId(), original.imagingType(), original.operational(),
                    original.compatible(), hasCapacity, original.assignedTechnicianId(), original.roomVersion());
        }
    }
}
