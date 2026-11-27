package fu.fa26.sep490.g58.otcms_be.common.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.common.exception.CoordinationGuardUnavailableException;
import fu.fa26.sep490.g58.otcms_be.common.repository.CoordinationGuardRepository;
import org.junit.jupiter.api.Test;

/** The booking coordination lock is only taken inside a read-committed transaction and only when the lock row is granted. */
class CoordinationGuardServiceImplTest {
    private final CoordinationGuardRepository repository = mock(CoordinationGuardRepository.class);
    private final CoordinationGuardServiceImpl service = new CoordinationGuardServiceImpl(repository);

    @Test
    void acquireLocksTheGuardWhenTheTransactionRunsReadCommitted() {
        when(repository.transactionIsolation()).thenReturn("read committed");
        when(repository.lockGuardRow()).thenReturn(true);

        service.acquire();

        verify(repository).setTransactionTimeouts();
        verify(repository).lockGuardRow();
    }

    @Test
    void acquireAcceptsTheIsolationLevelNameInAnyLetterCase() {
        when(repository.transactionIsolation()).thenReturn("READ COMMITTED");
        when(repository.lockGuardRow()).thenReturn(true);

        assertDoesNotThrow(service::acquire);
    }

    @Test
    void acquireRefusesAnyOtherIsolationLevelWithoutTakingTheLock() {
        when(repository.transactionIsolation()).thenReturn("serializable");

        var failure = assertThrows(CoordinationGuardUnavailableException.class, service::acquire);

        assertEquals("DEPENDENCY_UNAVAILABLE", failure.getMessage());
        verify(repository, never()).lockGuardRow();
    }

    @Test
    void acquireFailsWhenTheGuardRowIsNotGranted() {
        when(repository.transactionIsolation()).thenReturn("read committed");
        when(repository.lockGuardRow()).thenReturn(false);

        var failure = assertThrows(CoordinationGuardUnavailableException.class, service::acquire);

        assertEquals("DEPENDENCY_UNAVAILABLE", failure.getMessage());
    }
}
