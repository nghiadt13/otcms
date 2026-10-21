package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.ClinicVisitJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitAuditJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitInitialReceptionJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.WalkInReceptionReplayJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitAuditJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitInitialReceptionJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.WalkInReceptionReplayJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.CreateWalkInVisitCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.CreateWalkInVisitServiceImpl;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

    private final CoordinationGuardService guard = org.mockito.Mockito.mock(CoordinationGuardService.class);
    private final ClinicVisitJpaRepository visits = org.mockito.Mockito.mock(ClinicVisitJpaRepository.class);
    private final VisitInitialReceptionJpaRepository receptions = org.mockito.Mockito.mock(VisitInitialReceptionJpaRepository.class);
    private final VisitAuditJpaRepository audits = org.mockito.Mockito.mock(VisitAuditJpaRepository.class);
    private final WalkInReceptionReplayJpaRepository replays = org.mockito.Mockito.mock(WalkInReceptionReplayJpaRepository.class);
    private CreateWalkInVisitServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CreateWalkInVisitServiceImpl(guard, visits, receptions, audits, replays,
                Clock.fixed(ARRIVAL, ZoneOffset.UTC));
    }

    @Test
    void acquiresGuardBeforeReplayLookupAndPersistsAllEvidenceWithOneInstant() {
        UUID priorAppointmentId = UUID.randomUUID();
        when(replays.findByReceptionistAccountIdAndIdempotencyKey(ACTOR, "key-1"))
                .thenReturn(Optional.empty());
        when(visits.saveAndFlush(any(ClinicVisitJpaEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(replays.saveAndFlush(any(WalkInReceptionReplayJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create(command(priorAppointmentId));

        assertEquals(PATIENT, result.patientProfileId());
        assertEquals(priorAppointmentId, result.priorAppointmentId());
        assertEquals(ARRIVAL, result.arrivedAt());
        ArgumentCaptor<ClinicVisitJpaEntity> visitCaptor = ArgumentCaptor.forClass(ClinicVisitJpaEntity.class);
        verify(visits).saveAndFlush(visitCaptor.capture());
        assertEquals(ARRIVAL, visitCaptor.getValue().getArrivedAt());
        assertEquals(ARRIVAL, visitCaptor.getValue().getCheckedInAt());
        verify(receptions).save(any(VisitInitialReceptionJpaEntity.class));
        verify(audits).save(any(VisitAuditJpaEntity.class));
        verify(replays).saveAndFlush(any(WalkInReceptionReplayJpaEntity.class));
        inOrder(guard, replays).verify(guard).acquire();
        inOrder(guard, replays).verify(replays).findByReceptionistAccountIdAndIdempotencyKey(ACTOR, "key-1");
    }

    @Test
    void exactReplayReturnsTheExistingVisitAfterAcquiringGuardAndWritesNothing() {
        var replay = WalkInReceptionReplayJpaEntity.create(ACTOR, "key-1", fingerprint(null), VISIT);
        var persistedVisit = ClinicVisitJpaEntity.walkIn(VISIT, PATIENT, null, ACTOR, ARRIVAL);
        when(replays.findByReceptionistAccountIdAndIdempotencyKey(ACTOR, "key-1"))
                .thenReturn(Optional.of(replay));
        when(visits.findById(VISIT)).thenReturn(Optional.of(persistedVisit));

        var result = service.create(command(null));

        assertEquals(VISIT, result.visitId());
        assertEquals(ARRIVAL, result.arrivedAt());
        verify(guard).acquire();
        verify(visits, never()).saveAndFlush(any());
        verify(receptions, never()).save(any());
        verify(audits, never()).save(any());
        verify(replays, never()).saveAndFlush(any());
    }

    @Test
    void conflictingReplayFailsBeforeWritingEvidence() {
        var replay = WalkInReceptionReplayJpaEntity.create(ACTOR, "key-1", "different-fingerprint", VISIT);
        when(replays.findByReceptionistAccountIdAndIdempotencyKey(ACTOR, "key-1"))
                .thenReturn(Optional.of(replay));

        var failure = assertThrows(VisitOperationException.class, () -> service.create(command(null)));

        assertEquals("IDEMPOTENCY_CONFLICT", failure.code());
        verify(guard).acquire();
        verify(visits, never()).saveAndFlush(any());
        verify(receptions, never()).save(any());
        verify(audits, never()).save(any());
        verify(replays, never()).saveAndFlush(any());
    }

    @Test
    void rejectsNonReceptionistBeforeDatabaseGuard() {
        var request = new CreateWalkInVisitCommand(ACTOR, AccountRole.PATIENT, PATIENT, null,
                "key-1", "correlation-1");

        var failure = assertThrows(VisitOperationException.class, () -> service.create(request));

        assertEquals("ACCESS_DENIED", failure.code());
        verify(guard, never()).acquire();
    }

    private static CreateWalkInVisitCommand command(UUID priorAppointmentId) {
        return new CreateWalkInVisitCommand(ACTOR, AccountRole.RECEPTIONIST, PATIENT, priorAppointmentId,
                "key-1", "correlation-1");
    }

    private static String fingerprint(UUID priorAppointmentId) {
        String canonical = "visit-walk-in-v1\n" + ACTOR + "\n" + PATIENT + "\n"
                + (priorAppointmentId == null ? "" : priorAppointmentId);
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
