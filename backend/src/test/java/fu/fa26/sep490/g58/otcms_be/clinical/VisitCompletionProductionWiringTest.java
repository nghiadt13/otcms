package fu.fa26.sep490.g58.otcms_be.clinical;

import fu.fa26.sep490.g58.otcms_be.visit.service.impl.VisitAuditRecorder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.model.*;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalVisitCompletionEvidenceServiceImpl;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl.DiagnosticVisitCompletionEvidenceServiceImpl;
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.ClinicVisitJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.*;
import fu.fa26.sep490.g58.otcms_be.visit.service.*;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.WalkInVisitLifecycleServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

/** Real Visit command, owner queries and transaction proxies; persistence/side-effect boundaries doubled. */
class VisitCompletionProductionWiringTest {
    AnnotationConfigWebApplicationContext context;
    final UUID visit = UUID.randomUUID(), patient = UUID.randomUUID(), actor = UUID.randomUUID();
    final UUID doctor = UUID.randomUUID();
    final UUID request = UUID.randomUUID(), attempt = UUID.randomUUID(), report = UUID.randomUUID();
    final Instant now = Instant.parse("2026-11-05T08:00:00Z");
    ServiceOrderJpaEntity order;
    DiagnosticReportJpaEntity pdf;
    <T> T bean(Class<T> type) { return context.getBean(type); }
    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(DoctorCoreProductionWiringTest.ProductionConfiguration.class, Extras.class,
                ClinicalVisitCompletionEvidenceServiceImpl.class, DiagnosticVisitCompletionEvidenceServiceImpl.class,
                WalkInVisitLifecycleServiceImpl.class); context.refresh();
        when(bean(AccountRepository.class).existsByIdAndAccountTypeAndStatus(eq(actor), eq(AccountType.RECEPTIONIST), any())).thenReturn(true);
        when(bean(ClinicVisitJpaRepository.class).findByIdForUpdate(visit)).thenReturn(Optional.of(ClinicVisitJpaEntity.walkIn(visit, patient, null, actor, now, null, null)));
        when(bean(WalkInVisitLifecycleRepository.class).closeOpenVisit(eq(visit), any())).thenReturn(true);
        when(bean(VisitExamQueueService.class).find(visit)).thenReturn(Optional.of(new fu.fa26.sep490.g58.otcms_be.visit.model.ExamQueueEntry(visit, doctor, null, null, 1, fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus.IN_EXAMINATION, now.atZone(java.time.ZoneOffset.UTC).toLocalDate(), now, null, null, null)));
        when(bean(VisitReviewSourceService.class).find(visit)).thenReturn(Optional.empty());
        when(bean(ClinicalExaminationRepository.class).findByVisitId(visit)).thenReturn(Optional.of(
                ClinicalExaminationJpaEntity.create(visit, "Reason", "Symptoms", "Findings", null, null, null, ClinicalExaminationStatus.COMPLETED, now)));
        order = orderedImaging(); order.markPaid(now); order.markResult(now);
        when(bean(ServiceOrderRepository.class).findByVisitId(visit)).thenReturn(List.of(order));
        diagnostic = mock(ClinicalServiceOrderExecutionPort.ImagingExecution.class);
        when(diagnostic.orderId()).thenReturn(order.getId());
        when(diagnostic.visitId()).thenReturn(visit);
        when(diagnostic.patientId()).thenReturn(patient);
        when(diagnostic.roomId()).thenReturn(UUID.randomUUID());
        when(diagnostic.attemptId()).thenReturn(attempt);
        when(diagnostic.studyRef()).thenReturn("study");
        when(diagnostic.status()).thenReturn(ClinicalServiceOrderExecutionPort.ExecutionStatus.REVIEWED);
        when(bean(ClinicalServiceOrderExecutionPort.class).findAllByOrderIds(anyCollection()))
                .thenAnswer(c -> { assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); return List.of(diagnostic); });
        pdf = mock(DiagnosticReportJpaEntity.class);
        when(pdf.getRequestId()).thenReturn(order.getId());
        when(pdf.getVisitId()).thenReturn(visit);
        when(pdf.getStudyRef()).thenReturn("study");
        when(pdf.getCompletedAt()).thenReturn(now);
        when(pdf.getReportId()).thenReturn(report);
        when(pdf.getValidationStatus()).thenReturn(DiagnosticReportValidationStatus.VALIDATED);
        when(pdf.getValidatedAt()).thenReturn(now);
        when(pdf.getReleasedAt()).thenReturn(now);
        when(bean(DiagnosticReportJpaRepository.class).findAllByRequestIdInAndState(anyCollection(), eq(DiagnosticFindingsVersionState.COMPLETED)))
                .thenReturn(List.of(pdf));
    }
    ClinicalServiceOrderExecutionPort.ImagingExecution diagnostic;
    ServiceOrderJpaEntity orderedImaging() {
        return new ServiceOrderJpaEntity("SO-COMPLETE", UUID.randomUUID(), visit, patient, UUID.randomUUID(), "MRI", ClinicalServiceType.IMAGING,
                UUID.randomUUID(), "Reason", "Knee", "Instructions", BigDecimal.ONE, now);
    }
    @AfterEach void stop() { if (context != null) context.close(); }
    void close() { bean(WalkInVisitLifecycleService.class).completeByDoctor(visit, doctor, actor, "phase3"); }
    @Test void unpaidOrPaidUndispatchedOrderBlocksActualVisitClosure() {
        for (boolean paid : List.of(false, true)) {
            order = orderedImaging(); if (paid) order.markPaid(now);
            when(bean(ServiceOrderRepository.class).findByVisitId(visit)).thenReturn(List.of(order));
            assertThrows(VisitOperationException.class, this::close);
        }
        verify(bean(WalkInVisitLifecycleRepository.class), never()).closeOpenVisit(any(), any());
        verifyNoInteractions(bean(VisitAuditRecorder.class), bean(NotificationPublisher.class), bean(ExitWalkInReceptionQueueService.class));
    }

    @Test void actualOwnerQueriesAllowCompleteImagingAndPreserveClosureEffects() {
        close(); verify(bean(WalkInVisitLifecycleRepository.class)).closeOpenVisit(eq(visit), any());
        verify(bean(WalkInVisitLifecycleRepository.class)).releaseReservedCare(eq(visit), any());
        verify(bean(VisitAuditRecorder.class)).visitClosed(eq(actor), eq(visit), any(), any()); verify(bean(NotificationPublisher.class)).publishToPatient(eq(patient), any());
        assertEquals(1, bean(DoctorCoreProductionWiringTest.RecordingTransactions.class).commits);
    }
    @Test void actualOwnerQueryRejectsUnreleasedReportBeforeAnyClosureEffects() {
        when(pdf.getReleasedAt()).thenReturn(null); assertThrows(VisitOperationException.class, this::close);
        verify(bean(WalkInVisitLifecycleRepository.class), never()).closeOpenVisit(any(), any());
        verifyNoInteractions(bean(VisitAuditRecorder.class), bean(NotificationPublisher.class), bean(ExitWalkInReceptionQueueService.class));
        assertEquals(1, bean(DoctorCoreProductionWiringTest.RecordingTransactions.class).rollbacks);
    }
    @Test void canceledClinicalImagingDoesNotRequireDiagnosticFulfillment() {
        order = orderedImaging(); order.cancel("No longer required", now);
        when(bean(ServiceOrderRepository.class).findByVisitId(visit)).thenReturn(List.of(order));
        close();
        verify(bean(ClinicalServiceOrderExecutionPort.class), never()).findAllByOrderIds(anyCollection());
        verify(bean(WalkInVisitLifecycleRepository.class)).closeOpenVisit(eq(visit), any());
    }
    @Test void assignedPendingInProgressAndUncompletedResultAllBlockActualVisitClosure() {
        assertNotNull(diagnostic.roomId());
        for (var state : List.of(ClinicalServiceOrderExecutionPort.ExecutionStatus.PENDING, ClinicalServiceOrderExecutionPort.ExecutionStatus.IN_PROGRESS, ClinicalServiceOrderExecutionPort.ExecutionStatus.RESULT_AVAILABLE)) {
            when(diagnostic.status()).thenReturn(state);
            if (state == ClinicalServiceOrderExecutionPort.ExecutionStatus.RESULT_AVAILABLE) {
                when(bean(DiagnosticReportJpaRepository.class).findAllByRequestIdInAndState(anyCollection(), eq(DiagnosticFindingsVersionState.COMPLETED)))
                        .thenReturn(List.of());
            }
            assertThrows(VisitOperationException.class, this::close, state.name());
        }
        verify(bean(WalkInVisitLifecycleRepository.class), never()).closeOpenVisit(any(), any());
        verifyNoInteractions(bean(VisitAuditRecorder.class), bean(NotificationPublisher.class), bean(ExitWalkInReceptionQueueService.class));
    }

    @Configuration static class Extras {
        @Bean fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties bookingProperties() { return mock(fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties.class); }
        @Bean WalkInVisitLifecycleRepository lifecycleRows() { return mock(WalkInVisitLifecycleRepository.class); }
        @Bean VisitAuditRecorder auditRows() { return mock(VisitAuditRecorder.class); }
        @Bean ExitWalkInReceptionQueueService queueExit() { return mock(ExitWalkInReceptionQueueService.class); }
        @Bean NotificationPublisher notifications() { return mock(NotificationPublisher.class); }
        @Bean StaffAvailabilityProviderQueryService names() { return mock(StaffAvailabilityProviderQueryService.class); }
        @Bean VisitExamQueueService examQueue() { return mock(VisitExamQueueService.class); }
        @Bean ManageVisitCareAssignmentService careAssignments() { return mock(ManageVisitCareAssignmentService.class); }
    }
}
