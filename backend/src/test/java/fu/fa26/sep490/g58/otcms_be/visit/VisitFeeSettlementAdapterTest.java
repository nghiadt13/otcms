package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.visit.integration.billing.VisitFeeSettlementAdapter;
import fu.fa26.sep490.g58.otcms_be.visit.model.ExamQueueEntry;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitExamQueueService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class VisitFeeSettlementAdapterTest {
    private static final Instant NOW = Instant.parse("2026-11-11T03:00:00Z");
    private static final UUID VISIT = UUID.fromString("0d0d0d0d-1e1e-4f2f-8a3a-4b4b4b4b4b4b");

    private final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    private final VisitExamQueueService examQueue = mock(VisitExamQueueService.class);
    private final VisitFeeSettlementAdapter adapter = new VisitFeeSettlementAdapter(guard, examQueue,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void admitsWalkInWaitingForItsFeeAfterAcquiringTheGuard() {
        when(examQueue.find(VISIT)).thenReturn(Optional.of(entry(VisitExamStatus.PENDING_PAYMENT)));

        adapter.onVisitInvoicePaid(VISIT);

        var order = inOrder(guard, examQueue);
        order.verify(guard).acquire();
        order.verify(examQueue).admitAfterPayment(VISIT, NOW);
    }

    @ParameterizedTest
    @EnumSource(value = VisitExamStatus.class, names = {"WAITING", "IN_EXAMINATION", "AWAITING_CONCLUSION", "COMPLETED", "CANCELLED"})
    void leavesAnyOtherStateAloneWithoutFailingThePayment(VisitExamStatus status) {
        when(examQueue.find(VISIT)).thenReturn(Optional.of(entry(status)));

        assertDoesNotThrow(() -> adapter.onVisitInvoicePaid(VISIT));

        verify(examQueue, never()).admitAfterPayment(any(), any());
    }

    @Test
    void visitWithoutQueueRowIsNotAnErrorForThePayment() {
        when(examQueue.find(VISIT)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> adapter.onVisitInvoicePaid(VISIT));

        verify(guard).acquire();
        verify(examQueue, never()).admitAfterPayment(any(), any());
    }

    private static ExamQueueEntry entry(VisitExamStatus status) {
        return new ExamQueueEntry(VISIT, UUID.randomUUID(), null, UUID.randomUUID(),
                status == VisitExamStatus.PENDING_PAYMENT ? null : 1, status, LocalDate.of(2026, 11, 11), NOW,
                null, null, null);
    }
}
