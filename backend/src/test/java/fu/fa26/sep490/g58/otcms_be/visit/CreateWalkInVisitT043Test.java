package fu.fa26.sep490.g58.otcms_be.visit;

import fu.fa26.sep490.g58.otcms_be.visit.service.impl.VisitAuditRecorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.exception.ClinicServiceException;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService;
import fu.fa26.sep490.g58.otcms_be.staff.exception.DoctorProfileException;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorWorkShift;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorScheduleService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.ExamQueueEntry;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.ClinicVisitJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitRoomJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.QueueEntryJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.WalkInReceptionQueueEntryJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.CreateWalkInVisitCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitExamQueueService;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.CreateWalkInVisitServiceImpl;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreateWalkInVisitT043Test {
    private static final Instant ARRIVAL = Instant.parse("2026-10-18T15:00:00Z");
    private static final UUID ACTOR = UUID.fromString("c0bfb4db-09ac-4169-b9df-c82d23c0d53e");
    private static final UUID PATIENT = UUID.fromString("afddbb05-c553-4ea6-98e7-479f69e64278");
    private static final UUID VISIT = UUID.fromString("5f2fd088-c59a-463e-b70b-8ae87c281ed2");
    private static final UUID DOCTOR = UUID.fromString("11111111-2222-4333-8444-555555555555");
    private static final UUID OTHER_DOCTOR = UUID.fromString("66666666-7777-4888-8999-aaaaaaaaaaaa");
    private static final UUID SERVICE = UUID.fromString("9a9a9a9a-bbbb-4ccc-8ddd-eeeeeeeeeeee");
    private static final UUID ROOM = UUID.fromString("12121212-3434-4565-8787-909090909090");

    private final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    private final ClinicVisitJpaRepository visits = mock(ClinicVisitJpaRepository.class);
    private final VisitAuditRecorder audits = mock(VisitAuditRecorder.class);
    private final WalkInReceptionQueueEntryJpaRepository queueEntries = mock(WalkInReceptionQueueEntryJpaRepository.class);
    private final VisitExamQueueService examQueue = mock(VisitExamQueueService.class);
    private final DoctorScheduleService schedules = mock(DoctorScheduleService.class);
    private final DoctorProfileIdentityQueryService doctorProfiles = mock(DoctorProfileIdentityQueryService.class);
    private final GetClinicServiceCatalogService catalog = mock(GetClinicServiceCatalogService.class);
    private final VisitRoomJpaRepository rooms = mock(VisitRoomJpaRepository.class);
    private final fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService receptionists = mock(fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService.class);
    private CreateWalkInVisitServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CreateWalkInVisitServiceImpl(guard, visits, queueEntries, audits, receptionists,
                examQueue, schedules, doctorProfiles, catalog, rooms, Clock.fixed(ARRIVAL, ZoneOffset.UTC));
        when(schedules.forProfile(eq(DOCTOR), any(), any())).thenReturn(List.of(
                new DoctorWorkShift(UUID.randomUUID(), DOCTOR, ARRIVAL.minusSeconds(3600), ARRIVAL.plusSeconds(3600),
                        ROOM)));
        when(catalog.getService(SERVICE)).thenReturn(examination(new BigDecimal("250000.00")));
        when(doctorProfiles.findDoctorNames(anyCollection())).thenReturn(Map.of(DOCTOR, "Dr Test"));
        when(visits.saveAndFlush(any(ClinicVisitJpaEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(receptionists.isActiveReceptionistAccount(ACTOR)).thenReturn(true);
        when(queueEntries.saveAndFlush(any(QueueEntryJpaEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void acquiresGuardBeforeReplayLookupAndPersistsAllEvidenceWithOneInstant() {
        UUID priorAppointmentId = UUID.randomUUID();
        when(visits.findByCreatedByAccountIdAndWalkInIdempotencyKey(ACTOR, "key-1")).thenReturn(Optional.empty());
        when(examQueue.find(any())).thenReturn(Optional.of(entry(VisitExamStatus.PENDING_PAYMENT, null, null)));

        var result = service.create(command(priorAppointmentId, DOCTOR, SERVICE));

        assertEquals(PATIENT, result.patientProfileId());
        assertEquals(priorAppointmentId, result.priorAppointmentId());
        assertEquals(ARRIVAL, result.arrivedAt());
        assertEquals(VisitExamStatus.PENDING_PAYMENT, result.examStatus());
        assertNull(result.queueNumber());
        assertEquals("Dr Test", result.doctorName());
        assertEquals(new BigDecimal("250000.00"), result.feeAmount());
        ArgumentCaptor<ClinicVisitJpaEntity> visitCaptor = ArgumentCaptor.forClass(ClinicVisitJpaEntity.class);
        verify(visits).saveAndFlush(visitCaptor.capture());
        assertEquals(ARRIVAL, visitCaptor.getValue().getArrivedAt());
        assertEquals(ARRIVAL, visitCaptor.getValue().getCheckedInAt());
        verify(queueEntries).saveAndFlush(any(QueueEntryJpaEntity.class));
        verify(audits).walkInCreated(eq(ACTOR), any(), eq(ARRIVAL), eq("correlation-1"));
        verify(audits).queueEntered(any(), any(), eq(ACTOR), eq("correlation-1"));
        assertEquals("key-1", visitCaptor.getValue().getWalkInIdempotencyKey());
        assertEquals(fingerprint(priorAppointmentId, DOCTOR, SERVICE), visitCaptor.getValue().getWalkInRequestFingerprint());
        verify(examQueue).createPendingPayment(any(), eq(DOCTOR), eq(SERVICE), eq(ARRIVAL));
        verify(examQueue, never()).enqueueWaiting(any(), any(), any(), any());
        var order = inOrder(guard, visits);
        order.verify(guard).acquire();
        order.verify(visits).findByCreatedByAccountIdAndWalkInIdempotencyKey(ACTOR, "key-1");
    }

    @Test
    void freeServiceIsNumberedAtOnceWithoutPendingPayment() {
        when(catalog.getService(SERVICE)).thenReturn(examination(BigDecimal.ZERO));
        when(visits.findByCreatedByAccountIdAndWalkInIdempotencyKey(ACTOR, "key-1")).thenReturn(Optional.empty());
        when(examQueue.find(any())).thenReturn(Optional.of(entry(VisitExamStatus.WAITING, 1, ROOM)));
        VisitRoomJpaEntity roomOfShift = room("Phong kham 2");
        when(rooms.findById(ROOM)).thenReturn(Optional.of(roomOfShift));

        var result = service.create(command(null, DOCTOR, SERVICE));

        assertEquals(VisitExamStatus.WAITING, result.examStatus());
        assertEquals(1, result.queueNumber());
        assertEquals("Phong kham 2", result.roomLabel());
        assertEquals(BigDecimal.ZERO, result.feeAmount());
        verify(examQueue).enqueueWaiting(any(), eq(DOCTOR), eq(SERVICE), eq(ARRIVAL));
        verify(examQueue, never()).createPendingPayment(any(), any(), any(), any());
    }

    @Test
    void exactReplayReturnsTheExistingVisitAfterAcquiringGuardAndWritesNothing() {
        var replay = ClinicVisitJpaEntity.walkIn(VISIT, PATIENT, null, ACTOR, ARRIVAL, "key-1", fingerprint(null, DOCTOR, SERVICE));
        when(visits.findByCreatedByAccountIdAndWalkInIdempotencyKey(ACTOR, "key-1")).thenReturn(Optional.of(replay));
        when(examQueue.find(VISIT)).thenReturn(Optional.of(entry(VisitExamStatus.WAITING, 3, ROOM)));
        VisitRoomJpaEntity roomOfShift = room("Phong kham 1");
        when(rooms.findById(ROOM)).thenReturn(Optional.of(roomOfShift));

        var result = service.create(command(null, DOCTOR, SERVICE));

        assertEquals(VISIT, result.visitId());
        assertEquals(ARRIVAL, result.arrivedAt());
        assertEquals(3, result.queueNumber());
        assertEquals("Phong kham 1", result.roomLabel());
        verify(guard).acquire();
        verifyNoInteractions(schedules);
        verify(visits, never()).saveAndFlush(any());
        verifyNoInteractions(audits);
        verify(examQueue, never()).createPendingPayment(any(), any(), any(), any());
        verify(examQueue, never()).enqueueWaiting(any(), any(), any(), any());
    }

    @Test
    void conflictingReplayFailsBeforeWritingEvidence() {
        var replay = ClinicVisitJpaEntity.walkIn(VISIT, PATIENT, null, ACTOR, ARRIVAL, "key-1", "different-fingerprint");
        when(visits.findByCreatedByAccountIdAndWalkInIdempotencyKey(ACTOR, "key-1")).thenReturn(Optional.of(replay));

        var failure = assertThrows(VisitOperationException.class, () -> service.create(command(null, DOCTOR, SERVICE)));

        assertEquals("IDEMPOTENCY_CONFLICT", failure.code());
        verify(guard).acquire();
        verify(visits, never()).saveAndFlush(any());
        verifyNoInteractions(audits);
    }

    @Test
    void sameKeyWithAnotherDoctorIsAConflict() {
        var replay = ClinicVisitJpaEntity.walkIn(VISIT, PATIENT, null, ACTOR, ARRIVAL, "key-1", fingerprint(null, DOCTOR, SERVICE));
        when(visits.findByCreatedByAccountIdAndWalkInIdempotencyKey(ACTOR, "key-1")).thenReturn(Optional.of(replay));

        var failure = assertThrows(VisitOperationException.class,
                () -> service.create(command(null, OTHER_DOCTOR, SERVICE)));

        assertEquals("IDEMPOTENCY_CONFLICT", failure.code());
        verify(visits, never()).saveAndFlush(any());
    }

    @Test
    void rejectsNonReceptionistBeforeDatabaseGuard() {
        var request = new CreateWalkInVisitCommand(ACTOR, AccountRole.PATIENT, PATIENT, null, DOCTOR, SERVICE,
                "key-1", "correlation-1");

        var failure = assertThrows(VisitOperationException.class, () -> service.create(request));

        assertEquals("ACCESS_DENIED", failure.code());
        verify(guard, never()).acquire();
    }

    @Test
    void doctorOffShiftIsRejectedBeforeAnyWrite() {
        when(visits.findByCreatedByAccountIdAndWalkInIdempotencyKey(ACTOR, "key-1")).thenReturn(Optional.empty());
        when(schedules.forProfile(eq(DOCTOR), any(), any())).thenReturn(List.of());

        var failure = assertThrows(VisitOperationException.class, () -> service.create(command(null, DOCTOR, SERVICE)));

        assertEquals("DOCTOR_NOT_ON_SHIFT", failure.code());
        verify(guard).acquire();
        verifyNoInteractions(audits, queueEntries, examQueue);
        verify(visits, never()).saveAndFlush(any());
    }

    @Test
    void unknownDoctorProfileIsReportedAsNotOnShift() {
        when(visits.findByCreatedByAccountIdAndWalkInIdempotencyKey(ACTOR, "key-1")).thenReturn(Optional.empty());
        when(schedules.forProfile(eq(DOCTOR), any(), any()))
                .thenThrow(new DoctorProfileException("DOCTOR_PROFILE_NOT_FOUND"));

        var failure = assertThrows(VisitOperationException.class, () -> service.create(command(null, DOCTOR, SERVICE)));

        assertEquals("DOCTOR_NOT_ON_SHIFT", failure.code());
        verifyNoInteractions(examQueue);
        verify(visits, never()).saveAndFlush(any());
    }

    @Test
    void serviceMustBeActiveExaminationWithKnownId() {
        when(visits.findByCreatedByAccountIdAndWalkInIdempotencyKey(ACTOR, "key-1")).thenReturn(Optional.empty());
        when(catalog.getService(SERVICE)).thenReturn(new ClinicService(SERVICE, "Xet nghiem", "",
                ClinicServiceType.LABORATORY, new BigDecimal("90000"), Duration.ofMinutes(15), Duration.ZERO, false,
                true));
        assertEquals("VALIDATION_FAILED", assertThrows(VisitOperationException.class,
                () -> service.create(command(null, DOCTOR, SERVICE))).code());

        when(catalog.getService(SERVICE)).thenReturn(new ClinicService(SERVICE, "Kham cu", "",
                ClinicServiceType.EXAMINATION, new BigDecimal("90000"), Duration.ofMinutes(30), Duration.ZERO, false,
                false));
        assertEquals("VALIDATION_FAILED", assertThrows(VisitOperationException.class,
                () -> service.create(command(null, DOCTOR, SERVICE))).code());

        when(catalog.getService(SERVICE)).thenThrow(new ClinicServiceException("SERVICE_NOT_FOUND"));
        assertEquals("VALIDATION_FAILED", assertThrows(VisitOperationException.class,
                () -> service.create(command(null, DOCTOR, SERVICE))).code());

        verifyNoInteractions(examQueue);
        verify(visits, never()).saveAndFlush(any());
    }

    private static CreateWalkInVisitCommand command(UUID priorAppointmentId, UUID doctorProfileId, UUID serviceId) {
        return new CreateWalkInVisitCommand(ACTOR, AccountRole.RECEPTIONIST, PATIENT, priorAppointmentId,
                doctorProfileId, serviceId, "key-1", "correlation-1");
    }

    private static ClinicService examination(BigDecimal price) {
        return new ClinicService(SERVICE, "Kham tong quat", "", ClinicServiceType.EXAMINATION, price,
                Duration.ofMinutes(30), Duration.ZERO, false, true);
    }

    private static ExamQueueEntry entry(VisitExamStatus status, Integer queueNumber, UUID roomId) {
        return new ExamQueueEntry(VISIT, DOCTOR, roomId, SERVICE, queueNumber, status, LocalDate.of(2026, 10, 18),
                ARRIVAL, null, null, null);
    }

    private static VisitRoomJpaEntity room(String label) {
        VisitRoomJpaEntity room = mock(VisitRoomJpaEntity.class);
        when(room.getDisplayLabel()).thenReturn(label);
        return room;
    }

    /** Same canonical form as CreateWalkInVisitServiceImpl (version v2: doctor and service are part of the request). */
    private static String fingerprint(UUID priorAppointmentId, UUID doctorProfileId, UUID serviceId) {
        String canonical = "visit-walk-in-v2\n" + ACTOR + "\n" + PATIENT + "\n"
                + (priorAppointmentId == null ? "" : priorAppointmentId) + "\n" + doctorProfileId + "\n" + serviceId;
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
