package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.billing.service.BillingRefundService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalUnperformedOrderService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalVisitCompletionEvidenceService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalVisitCompletionEvidenceService.ClinicalCompletionEvidence;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientClinicalIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.model.ExamQueueEntry;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.ClinicVisitJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.*;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitExamQueueService;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.VisitAuditRecorder;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.VisitLifecycleVisitTransaction;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class VisitLifecycleSweepServiceTest {
    final UUID visitId = UUID.randomUUID(), patient = UUID.randomUUID(), order = UUID.randomUUID();
    final Instant first = Instant.parse("2026-11-01T03:00:00Z");
    final ClinicVisitJpaRepository visits = mock(ClinicVisitJpaRepository.class);
    final VisitLifecycleRepository lifecycle = mock(VisitLifecycleRepository.class);
    final WalkInVisitLifecycleRepository writes = mock(WalkInVisitLifecycleRepository.class);
    final WalkInReceptionQueueEntryJpaRepository reception = mock(WalkInReceptionQueueEntryJpaRepository.class);
    final VisitExamQueueService queue = mock(VisitExamQueueService.class);
    final ClinicalUnperformedOrderService orders = mock(ClinicalUnperformedOrderService.class);
    final ClinicalVisitCompletionEvidenceService evidence = mock(ClinicalVisitCompletionEvidenceService.class);
    final BillingRefundService billing = mock(BillingRefundService.class);
    final NotificationPublisher notifications = mock(NotificationPublisher.class);
    final PatientClinicalIdentityQueryService identities = mock(PatientClinicalIdentityQueryService.class);
    final AppointmentBookingProperties booking = mock(AppointmentBookingProperties.class);
    final VisitLifecycleVisitTransaction service = new VisitLifecycleVisitTransaction(mock(CoordinationGuardService.class), visits,
            lifecycle, writes, reception, queue, orders, evidence, billing, notifications, identities,
            mock(VisitAuditRecorder.class), booking);
    final ClinicVisitJpaEntity visit = mock(ClinicVisitJpaEntity.class);

    @BeforeEach void setup() {
        when(booking.clinicTimeZone()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        when(visits.findByIdForUpdate(visitId)).thenReturn(Optional.of(visit));
        when(visit.getStatus()).thenReturn("OPEN");
        when(visit.getCheckedInAt()).thenReturn(first);
        when(visit.getPatientProfileId()).thenReturn(patient);
    }
    void exam(VisitExamStatus status) {
        when(queue.find(visitId)).thenReturn(Optional.of(new ExamQueueEntry(visitId, UUID.randomUUID(), null, null,
                1, status, LocalDate.of(2026, 11, 1), first, null, null, null)));
    }
    @Test void daySevenRemainsOpenAndDayEightCancelsUnperformedOrdersOnlyOnce() {
        exam(VisitExamStatus.AWAITING_CONCLUSION);
        Instant daySeven = Instant.parse("2026-11-07T10:00:00Z"), dayEight = Instant.parse("2026-11-08T00:15:00Z");
        assertFalse(service.markIncompleteIfDue(visitId, daySeven));
        verifyNoInteractions(orders, billing, notifications);
        when(orders.cancelUnperformedOrders(eq(visitId), anyString())).thenReturn(List.of(order));
        when(lifecycle.markIncomplete(visitId, dayEight)).thenReturn(true);
        when(identities.findClinicalIdentities(List.of(patient))).thenReturn(Map.of());
        assertTrue(service.markIncompleteIfDue(visitId, dayEight));
        verify(billing).voidUnpaidServiceOrder(order);
        verify(notifications).publishToPatient(eq(patient), any());
        when(visit.getStatus()).thenReturn("INCOMPLETE");
        assertFalse(service.markIncompleteIfDue(visitId, dayEight));
        verify(orders, times(1)).cancelUnperformedOrders(eq(visitId), anyString());
    }
    @Test void endOfDayClosesWaitingVisits() {
        exam(VisitExamStatus.WAITING);
        assertTrue(service.closeEndOfDay(visitId, first));
        verify(queue).cancel(visitId, first);
        verify(writes).closeOpenVisit(visitId, first);
    }
    @Test void endOfDayKeepsExamWithEvidenceAndClosesExamWithoutEvidence() {
        exam(VisitExamStatus.IN_EXAMINATION);
        when(evidence.getCompletionEvidence(visitId)).thenReturn(new ClinicalCompletionEvidence(visitId, true, false, 0, 0, true, List.of()));
        assertTrue(service.closeEndOfDay(visitId, first));
        verify(queue).markAwaitingConclusion(visitId, first);
        verify(writes, never()).closeOpenVisit(any(), any());
        when(evidence.getCompletionEvidence(visitId)).thenReturn(new ClinicalCompletionEvidence(visitId, false, false, 0, 0, true, List.of()));
        assertTrue(service.closeEndOfDay(visitId, first));
        verify(queue).cancel(visitId, first);
        verify(writes).closeOpenVisit(visitId, first);
    }
}
