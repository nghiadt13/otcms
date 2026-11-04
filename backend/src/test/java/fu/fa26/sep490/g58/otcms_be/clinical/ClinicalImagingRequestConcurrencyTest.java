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
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
            List.of(new ImagingOrderRequest(UUID.randomUUID(), "Reason", "Knee", null)));

    @Test void replayUniqueCollisionReloadsWinnerOutsideFailedAttempt() {
        var conflict = unique("uk_clinical_imaging_replay_doctor_key");
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
        var failure = unique("uk_clinical_imaging_replay_doctor_key");
        when(transactions.submit(any(), any())).thenThrow(failure);
        when(transactions.recoverReplay(any(), any())).thenReturn(Optional.empty());
        assertSame(failure, assertThrows(DataIntegrityViolationException.class, () -> service.submit(command)));
    }

    @Test void replayConflictFromWinnerIsNotSuppressed() {
        when(transactions.submit(any(), any())).thenThrow(unique("uk_clinical_imaging_replay_doctor_key"));
        when(transactions.recoverReplay(any(), any())).thenThrow(new ClinicalOperationException("IDEMPOTENCY_CONFLICT"));
        assertEquals("IDEMPOTENCY_CONFLICT", assertThrows(ClinicalOperationException.class, () -> service.submit(command)).code());
    }

    @Test void actualSpringTransactionInterceptorRollsBackBeforeWinnerReload() {
        var events = new ArrayList<String>();
        var manager = new RecordingTransactions(events);
        var preparation = mock(ClinicalOrderPreparationService.class);
        var writer = mock(ClinicalOrderingTransactionService.class);
        var replays = mock(ClinicalImagingRequestReplayRepository.class);
        var rounds = mock(OrderingRoundRepository.class);
        var orders = mock(ServiceOrderRepository.class);
        var winner = new OrderingRoundJpaEntity(visitId, UUID.randomUUID(), 1, doctorId, Instant.EPOCH);
        String hash = ClinicalImagingRequestFingerprint.fingerprint(command);
        var stored = new ClinicalImagingRequestReplayJpaEntity(doctorId, "key", hash, winner.getId(), Instant.EPOCH);
        var lookups = new AtomicInteger();
        when(replays.findByDoctorProfileIdAndIdempotencyKey(doctorId, "key")).thenAnswer(invocation -> {
            if (lookups.getAndIncrement() == 0) return Optional.empty();
            assertTrue(events.contains("rollback"), "Winner lookup must follow completed loser rollback");
            events.add("winner-read");
            return Optional.of(stored);
        });
        when(preparation.requireCurrentCare(visitId, doctorId)).thenReturn(
                new VisitClinicalContextPort.ClinicalVisitContext(visitId, UUID.randomUUID(), doctorId, true));
        when(preparation.prepare(anyList(), eq(true))).thenReturn(List.of());
        when(writer.createRound(any(), any(), any(), anyList())).thenAnswer(invocation -> {
            events.add("round-and-orders-attempted"); return round();
        });
        when(replays.saveAndFlush(any())).thenThrow(unique("uk_clinical_imaging_replay_doctor_key"));
        when(rounds.findById(winner.getId())).thenReturn(Optional.of(winner));
        when(orders.findByOrderingRoundId(winner.getId())).thenReturn(List.of());
        var target = new ClinicalImagingRequestTransactionService(preparation, writer, replays, rounds, orders, Clock.systemUTC());
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        var coordinator = new ClinicalImagingRequestServiceImpl((ClinicalImagingRequestTransactionService) proxy.getProxy());
        var result = coordinator.submit(command);
        assertTrue(result.replayed()); assertEquals(winner.getId(), result.round().id());
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
