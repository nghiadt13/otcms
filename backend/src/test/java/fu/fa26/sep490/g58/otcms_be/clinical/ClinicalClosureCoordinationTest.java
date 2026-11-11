package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.*;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ClinicalClosureCoordinationTest {
    final UUID visit = UUID.randomUUID(), patient = UUID.randomUUID(), doctor = UUID.randomUUID();
    final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    final VisitClinicalContextPort visits = mock(VisitClinicalContextPort.class);
    final OrderingRoundRepository rounds = mock(OrderingRoundRepository.class);
    final ServiceOrderRepository orders = mock(ServiceOrderRepository.class);
    final ClinicalExaminationRepository exams = mock(ClinicalExaminationRepository.class);
    final ClinicalDiagnosisRepository diagnoses = mock(ClinicalDiagnosisRepository.class);
    final ClinicalOrderPreparationService preparation = new ClinicalOrderPreparationService(visits, mock(ClinicServiceCatalogPort.class));
    @Test void orderCancellationAcquiresSharedGuardBeforeReadingOrders() {
        var ordering = new fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalOrderingServiceImpl(
                mock(ClinicalOrderPreparationService.class), mock(ClinicalOrderingTransactionService.class), rounds, orders,
                Clock.systemUTC(), guard);
        doThrow(new IllegalStateException("guard unavailable")).when(guard).acquire();
        assertThrows(IllegalStateException.class, () -> ordering.cancel(UUID.randomUUID(), "Declined"));
        verifyNoInteractions(rounds, orders);
    }

    @Test void delayedOrderWriterRechecksClosedVisitAfterGuardBeforeAnyPersistence() {
        doAnswer(call -> { when(visits.getVisitContext(visit)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(visit, patient, doctor, false)); return null; }).when(guard).acquire();
        var writer = new ClinicalOrderingTransactionService(rounds, orders, Clock.systemUTC(), guard, preparation);
        assertEquals("MSG-42", assertThrows(ClinicalOperationException.class, () -> writer.createRound(visit, patient, doctor, List.of())).code());
        var sequence = inOrder(guard, visits); sequence.verify(guard).acquire(); sequence.verify(visits).getVisitContext(visit);
        verifyNoInteractions(rounds, orders);
    }
    @Test void guardFailurePreventsExaminationReadAndDowngrade() {
        doThrow(new IllegalStateException("guard unavailable")).when(guard).acquire();
        var writer = new ClinicalExaminationServiceImpl(visits, exams, diagnoses, Clock.systemUTC(), guard);
        assertThrows(IllegalStateException.class, () -> writer.saveDraft(command()));
        verifyNoInteractions(visits, exams, diagnoses);
    }
    @Test void closedVisitPreventsDraftDowngradeAfterGuard() {
        when(visits.getVisitContext(visit)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(visit, patient, doctor, false));
        var writer = new ClinicalExaminationServiceImpl(visits, exams, diagnoses, Clock.systemUTC(), guard);
        assertEquals("MSG-42", assertThrows(ClinicalOperationException.class, () -> writer.saveDraft(command())).code());
        var sequence = inOrder(guard, visits); sequence.verify(guard).acquire(); sequence.verify(visits).getVisitContext(visit);
        verifyNoInteractions(exams, diagnoses);
    }
    @Test void uc38AcquiresGuardBeforeFirstVisitReadIncludingReplay() {
        var prep = mock(ClinicalOrderPreparationService.class);
        var replays = mock(ClinicalImagingRequestReplayRepository.class);
        var tx = new ClinicalImagingRequestTransactionService(prep, mock(ClinicalOrderingTransactionService.class), replays,
                rounds, orders, Clock.systemUTC(), guard, mock(fu.fa26.sep490.g58.otcms_be.clinical.service.VisitConclusionPort.class));
        doThrow(new IllegalStateException("guard unavailable")).when(guard).acquire();
        var command = new ClinicalImagingRequestService.ImagingRequestCommand(visit, doctor, "key", List.of(), true);
        assertThrows(IllegalStateException.class, () -> tx.submit(command, "fingerprint"));
        assertThrows(IllegalStateException.class, () -> tx.recoverReplay(command, "fingerprint"));
        verifyNoInteractions(prep, replays, rounds, orders);
    }
    ClinicalExaminationService.ExaminationCommand command() {
        return new ClinicalExaminationService.ExaminationCommand(visit, doctor, null, null, null, null, null, null, List.of());
    }
}
