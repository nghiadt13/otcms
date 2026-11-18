package fu.fa26.sep490.g58.otcms_be.diagnostic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.billing.service.BillingQueryService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ExecutionStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ImagingExecution;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticStatusService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl.DiagnosticAcquisitionServiceImpl;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.service.ServiceQueueService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** D7 (MSG-95) and the call-before-start rule of UC-46 on "Start acquisition". */
class DiagnosticAcquisitionPaymentGateTest {
    private final ClinicalServiceOrderExecutionPort executions = mock(ClinicalServiceOrderExecutionPort.class);
    private final BillingQueryService billing = mock(BillingQueryService.class);
    private final ServiceQueueService queue = mock(ServiceQueueService.class);
    private final DiagnosticAcquisitionServiceImpl service = new DiagnosticAcquisitionServiceImpl(executions,
            mock(DiagnosticStatusService.class), mock(AuditLogWriter.class), billing, queue);
    private final DiagnosticActor technician = DiagnosticActor.user(UUID.randomUUID(), AccountRole.IMAGING_TECHNICIAN);
    private final UUID order = UUID.randomUUID();

    private void pendingOrder() {
        ImagingExecution pending = mock(ImagingExecution.class);
        when(pending.status()).thenReturn(ExecutionStatus.PENDING);
        when(pending.executionVersion()).thenReturn(0L);
        when(executions.findForUpdate(order)).thenReturn(Optional.of(pending));
    }

    @Test
    void unpaidOrderCannotBeStarted() {
        pendingOrder();
        when(billing.isServiceOrderPaid(order)).thenReturn(false);
        var failure = assertThrows(DiagnosticOperationException.class, () -> service.start(technician, order));
        assertEquals(DiagnosticOperationOutcome.ORDER_NOT_PAID, failure.outcome());
        verify(executions, never()).recordFirstUse(any(), anyLong(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(queue);
    }

    @Test
    void patientWhoWasNotCalledCannotBeStarted() {
        pendingOrder();
        when(billing.isServiceOrderPaid(order)).thenReturn(true);
        doThrow(new VisitOperationException("MSG-42")).when(queue).markInProgress(order);
        var failure = assertThrows(DiagnosticOperationException.class, () -> service.start(technician, order));
        assertEquals(DiagnosticOperationOutcome.PATIENT_NOT_CALLED, failure.outcome());
        verify(executions, never()).recordFirstUse(any(), anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void paidAndCalledOrderStartsAndMovesTheQueueEntryToInProgress() {
        pendingOrder();
        when(billing.isServiceOrderPaid(order)).thenReturn(true);
        when(executions.recordFirstUse(eq(order), eq(0L), any(), any(), any(), any(), any(), any())).thenReturn(1);
        assertNotNull(service.start(technician, order));
        verify(queue).markInProgress(order);
    }

    @Test
    void cannotPerformLeavesTheRoomQueueButKeepsThePendingOrder() {
        pendingOrder();
        service.reportCannotPerform(technician, order, "  Bệnh nhân có kim loại trong người  ");
        verify(queue).leave(order);
        verify(executions, never()).cancel(any(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void cannotPerformNeedsAReasonAPendingOrderAndATechnician() {
        pendingOrder();
        assertThrows(IllegalArgumentException.class, () -> service.reportCannotPerform(technician, order, "  "));
        assertEquals(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD, assertThrows(DiagnosticOperationException.class,
                () -> service.reportCannotPerform(DiagnosticActor.user(UUID.randomUUID(), AccountRole.DOCTOR), order, "x")).outcome());
        ImagingExecution started = mock(ImagingExecution.class);
        when(started.status()).thenReturn(ExecutionStatus.IN_PROGRESS);
        when(executions.findForUpdate(order)).thenReturn(Optional.of(started));
        assertEquals(DiagnosticOperationOutcome.STATE_CONFLICT, assertThrows(DiagnosticOperationException.class,
                () -> service.reportCannotPerform(technician, order, "x")).outcome());
        verifyNoInteractions(queue);
    }
}
