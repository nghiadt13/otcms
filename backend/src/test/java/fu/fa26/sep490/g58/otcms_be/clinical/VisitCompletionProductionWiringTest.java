package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.model.*;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalVisitCompletionEvidenceServiceImpl;
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
        when(bean(ClinicVisitJpaRepository.class).findByIdForUpdate(visit)).thenReturn(Optional.of(ClinicVisitJpaEntity.walkIn(visit, patient, null, actor, now)));
        when(bean(WalkInVisitLifecycleRepository.class).closeOpenVisit(eq(visit), any())).thenReturn(true);
        when(bean(VisitReviewSourceService.class).find(visit)).thenReturn(Optional.empty());
        when(bean(ClinicalExaminationRepository.class).findByVisitId(visit)).thenReturn(Optional.of(
                ClinicalExaminationJpaEntity.create(visit, "Reason", "Symptoms", "Findings", null, null, null, ClinicalExaminationStatus.COMPLETED, now)));
        order = orderedImaging(); order.markResult(now);
        when(bean(ServiceOrderRepository.class).findByVisitId(visit)).thenReturn(List.of(order));
        var diagnostic = new DiagnosticRequestJpaEntity(request, visit, patient, UUID.randomUUID(), order.getServiceId(), UUID.randomUUID(), "MRI", DiagnosticRequestStatus.REVIEWED, null, null, attempt);
        diagnostic.setServiceOrderId(order.getId());
        when(bean(DiagnosticRequestJpaRepository.class).findAllByServiceOrderIdInAndStatusNot(anyCollection(), eq(DiagnosticRequestStatus.CANCELLED)))
                .thenAnswer(c -> { assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); return List.of(diagnostic); });
        when(bean(DiagnosticAttemptJpaRepository.class).findAllById(any())).thenReturn(List.of(new DiagnosticAttemptJpaEntity(attempt, request, 1,
                UUID.randomUUID(), "ACQUISITION_STARTED", "IMAGING", null, now, "study")));
        when(bean(DiagnosticFindingsVersionJpaRepository.class).findAllByRequestIdInAndState(anyCollection(), eq(DiagnosticFindingsVersionState.COMPLETED)))
                .thenReturn(List.of(new DiagnosticFindingsVersionJpaEntity(UUID.randomUUID(), request, visit, "study", 1, "Findings", UUID.randomUUID(), now, DiagnosticFindingsVersionState.COMPLETED, report, now)));
        pdf = new DiagnosticReportJpaEntity(report, request, visit, "study", 1, "private", "a".repeat(64), DiagnosticReportValidationStatus.VALIDATED, UUID.randomUUID(), now, now);
        when(bean(DiagnosticReportJpaRepository.class).findAllById(any())).thenReturn(List.of(pdf));
    }
    ServiceOrderJpaEntity orderedImaging() {
        return new ServiceOrderJpaEntity("SO-COMPLETE", UUID.randomUUID(), visit, patient, UUID.randomUUID(), "MRI", ClinicalServiceType.IMAGING,
                UUID.randomUUID(), "Reason", "Knee", "Instructions", BigDecimal.ONE, now);
    }
    @AfterEach void stop() { if (context != null) context.close(); }
    void close() { bean(WalkInVisitLifecycleService.class).close(visit, actor, AccountRole.RECEPTIONIST, "phase3"); }
    @Test void actualOwnerQueriesAllowCompleteImagingAndPreserveClosureEffects() {
        close(); verify(bean(WalkInVisitLifecycleRepository.class)).closeOpenVisit(eq(visit), any());
        verify(bean(WalkInVisitLifecycleRepository.class)).releaseReservedCare(eq(visit), any());
        verify(bean(VisitAuditJpaRepository.class)).saveAndFlush(any()); verify(bean(NotificationPublisher.class)).publishToPatient(eq(patient), any());
        assertEquals(1, bean(DoctorCoreProductionWiringTest.RecordingTransactions.class).commits);
    }
    @Test void actualOwnerQueryRejectsUnreleasedReportBeforeAnyClosureEffects() {
        pdf.setReleasedAt(null); assertThrows(VisitOperationException.class, this::close);
        verify(bean(WalkInVisitLifecycleRepository.class), never()).closeOpenVisit(any(), any());
        verifyNoInteractions(bean(VisitAuditJpaRepository.class), bean(NotificationPublisher.class), bean(ExitWalkInReceptionQueueService.class));
        assertEquals(1, bean(DoctorCoreProductionWiringTest.RecordingTransactions.class).rollbacks);
    }
    @Test void canceledClinicalImagingDoesNotRequireDiagnosticFulfillment() {
        order = orderedImaging(); order.cancel("No longer required", now);
        when(bean(ServiceOrderRepository.class).findByVisitId(visit)).thenReturn(List.of(order));
        close();
        verify(bean(DiagnosticRequestJpaRepository.class), never()).findAllByServiceOrderIdInAndStatusNot(anyCollection(), any());
        verify(bean(WalkInVisitLifecycleRepository.class)).closeOpenVisit(eq(visit), any());
    }
    @Configuration static class Extras {
        @Bean WalkInVisitLifecycleRepository lifecycleRows() { return mock(WalkInVisitLifecycleRepository.class); }
        @Bean VisitAuditJpaRepository auditRows() { return mock(VisitAuditJpaRepository.class); }
        @Bean ExitWalkInReceptionQueueService queueExit() { return mock(ExitWalkInReceptionQueueService.class); }
        @Bean NotificationPublisher notifications() { return mock(NotificationPublisher.class); }
        @Bean VisitReviewSourceService reviewSources() { return mock(VisitReviewSourceService.class); }
        @Bean StaffAvailabilityProviderQueryService names() { return mock(StaffAvailabilityProviderQueryService.class); }
    }
}
