package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalImagingRequestService.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.*;
import fu.fa26.sep490.g58.otcms_be.common.idempotency.IdempotencyReplay;
import fu.fa26.sep490.g58.otcms_be.common.idempotency.IdempotencyScope;
import fu.fa26.sep490.g58.otcms_be.common.idempotency.IdempotencyStore;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class ClinicalImagingRequestConcurrencyTest {
    private final ClinicalImagingRequestTransactionService transactions = mock(ClinicalImagingRequestTransactionService.class);
    private final ClinicalImagingRequestService service = new ClinicalImagingRequestServiceImpl(transactions);
    private final UUID visitId = UUID.randomUUID(), doctorId = UUID.randomUUID();
    private final ImagingRequestCommand command = new ImagingRequestCommand(visitId, doctorId, "key",
            List.of(new ImagingOrderRequest(UUID.randomUUID(), "Reason", "Knee", null)), true);

    @Test void replayUniqueCollisionReloadsWinnerOutsideFailedAttempt() {
        var conflict = duplicateSubmitKey();
        var winner = new SubmissionResult(round(), true);
        when(transactions.submit(any(), any())).thenThrow(conflict);
        when(transactions.recoverReplay(any(), any())).thenReturn(Optional.of(winner));
        assertSame(winner, service.submit(command));
        var sequence = inOrder(transactions);
        sequence.verify(transactions).submit(any(), any());
        sequence.verify(transactions).recoverReplay(any(), any());
    }

    @Test void unrelatedIntegrityFailureIsNeverConvertedIntoReplay() {
        var failure = unique("some_other_constraint");
        when(transactions.submit(any(), any())).thenThrow(failure);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class, () -> service.submit(command)));
        verify(transactions, never()).recoverReplay(any(), any());
    }

    @Test void boundedRoundNumberRetryIsDistinctFromReplayCollision() {
        var result = new SubmissionResult(round(), false);
        when(transactions.submit(any(), any())).thenThrow(unique("uk_clinical_round_visit_number")).thenReturn(result);
        assertSame(result, service.submit(command));
        verify(transactions, times(2)).submit(any(), any());
        verify(transactions, never()).recoverReplay(any(), any());
    }

    @Test void roundNumberContentionExhaustsAfterThreeAttempts() {
        when(transactions.submit(any(), any())).thenThrow(unique("uk_clinical_round_visit_number"));
        assertEquals("MSG-42", assertThrows(ClinicalOperationException.class, () -> service.submit(command)).code());
        verify(transactions, times(3)).submit(any(), any());
    }

    @Test void collisionWithoutCommittedWinnerPropagatesOriginalFailure() {
        var failure = duplicateSubmitKey();
        when(transactions.submit(any(), any())).thenThrow(failure);
        when(transactions.recoverReplay(any(), any())).thenReturn(Optional.empty());
        assertSame(failure, assertThrows(DataIntegrityViolationException.class, () -> service.submit(command)));
    }

    @Test void replayConflictFromWinnerIsNotSuppressed() {
        when(transactions.submit(any(), any())).thenThrow(duplicateSubmitKey());
        when(transactions.recoverReplay(any(), any())).thenThrow(new ClinicalOperationException("IDEMPOTENCY_CONFLICT"));
        assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(ClinicalOperationException.class, () -> service.submit(command)).code());
    }

    @Test void actualSpringTransactionInterceptorRollsBackBeforeWinnerReload() {
        var events = new ArrayList<String>();
        var manager = new RecordingTransactions(events);
        var preparation = mock(ClinicalOrderPreparationService.class);
        var writer = mock(ClinicalOrderingTransactionService.class);
        var orders = mock(ServiceOrderRepository.class);
        var rounds = mock(ClinicalOrderingRoundRepository.class);
        var store = mock(IdempotencyStore.class);
        String hash = ClinicalImagingRequestFingerprint.fingerprint(command);
        UUID roundId = UUID.randomUUID();
        var winner = new ServiceOrderJpaEntity("SO-WINNER", roundId, visitId,
                UUID.randomUUID(), command.orders().getFirst().serviceId(), "X-Ray",
                fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType.IMAGING, doctorId,
                "Reason", "Knee", null, java.math.BigDecimal.ONE, Instant.EPOCH);
        var lookups = new AtomicInteger();
        when(store.find(IdempotencyScope.ORDER_ROUND_SUBMIT, doctorId, "key")).thenAnswer(invocation -> {
            if (lookups.getAndIncrement() == 0) return Optional.empty();
            assertTrue(events.contains("rollback"), "Winner lookup must follow completed loser rollback");
            events.add("winner-read");
            return Optional.of(new IdempotencyReplay(hash, roundId, Instant.EPOCH));
        });
        when(rounds.findById(roundId)).thenReturn(Optional.of(
                new ClinicalOrderingRoundJpaEntity(roundId, visitId, 1, doctorId, Instant.EPOCH)));
        when(preparation.requireCurrentCare(visitId, doctorId)).thenReturn(
                new VisitClinicalContextPort.ClinicalVisitContext(visitId, UUID.randomUUID(), doctorId, true));
        when(preparation.prepare(anyList(), eq(true))).thenReturn(List.of());
        when(writer.createRound(any(), any(), any(), anyList(), any(), any())).thenAnswer(invocation -> {
            events.add("round-and-orders-attempted");
            throw duplicateSubmitKey();
        });
        when(orders.findByOrderingRoundId(roundId)).thenReturn(List.of(winner));
        var target = new ClinicalImagingRequestTransactionService(preparation, writer, orders, rounds, Clock.systemUTC(),
                mock(fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService.class),
                mock(fu.fa26.sep490.g58.otcms_be.clinical.service.VisitConclusionPort.class), store);
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        var coordinator = new ClinicalImagingRequestServiceImpl((ClinicalImagingRequestTransactionService) proxy.getProxy());
        var result = coordinator.submit(command);
        assertTrue(result.replayed()); assertEquals(roundId, result.round().id());
        assertEquals(List.of("begin", "round-and-orders-attempted", "rollback", "begin", "winner-read", "commit"), events);
    }

    @Test void transactionAnnotationsKeepRoundAndReplayInSameRequiredTransaction() throws Exception {
        assertEquals(Propagation.NOT_SUPPORTED, ClinicalImagingRequestServiceImpl.class.getMethod("submit", ImagingRequestCommand.class)
                .getAnnotation(Transactional.class).propagation());
        assertEquals(Propagation.REQUIRED, ClinicalImagingRequestTransactionService.class.getMethod("submit", ImagingRequestCommand.class, String.class)
                .getAnnotation(Transactional.class).propagation());
        assertEquals(Propagation.REQUIRED, ClinicalOrderingTransactionService.class
                .getMethod("createRound", UUID.class, UUID.class, UUID.class, List.class).getAnnotation(Transactional.class).propagation());
    }

    private ClinicalOrderingService.RoundView round() {
        return new ClinicalOrderingService.RoundView(UUID.randomUUID(), visitId, 1, doctorId, Instant.EPOCH, List.of());
    }
    /** What the writer raises when idempotency_records already holds the key (record() == false). */
    private static DuplicateKeyException duplicateSubmitKey() {
        return new DuplicateKeyException("ORDER_ROUND_SUBMIT key already recorded");
    }
    private static DataIntegrityViolationException unique(String name) {
        return new DataIntegrityViolationException("Write failed", new ConstraintViolationException(
                "Unique write rejected", new SQLException("duplicate key", "23505"), name));
    }
    private static class RecordingTransactions extends AbstractPlatformTransactionManager {
        private final List<String> events;
        RecordingTransactions(List<String> events) { this.events = events; }
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { events.add("begin"); }
        @Override protected void doCommit(DefaultTransactionStatus status) { events.add("commit"); }
        @Override protected void doRollback(DefaultTransactionStatus status) { events.add("rollback"); }
    }
}
