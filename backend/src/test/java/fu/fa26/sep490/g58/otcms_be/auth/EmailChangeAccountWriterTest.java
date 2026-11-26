package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.exception.EmailChangeWriteException;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.repository.EmailChangeAccountWriter;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailChangeAccountWriterTest {
    @org.junit.jupiter.api.Test
    void missingAccountIsAnOperationFailureAndDoesNotDetachIt() {
        var accounts = mock(AccountRepository.class);
        var manager = mock(EntityManager.class);
        var account = new AccountJpaEntity(); account.setId(UUID.randomUUID());
        when(accounts.updateVerifiedEmail(eq(account.getId()), anyString(), any())).thenReturn("NOT_FOUND");
        var failure = assertThrows(EmailChangeWriteException.class, () ->
                new EmailChangeAccountWriter(accounts, manager).updateVerifiedEmail(account, "target@example.test", Instant.now()));
        assertEquals(EmailChangeWriteException.Reason.OPERATION_FAILED, failure.reason());
        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
        verify(manager, never()).detach(any());
    }

    @ParameterizedTest @CsvSource({"duplicate,COLLISION", "operation,OPERATION_FAILED"})
    void persistenceFailuresAreMappedWithoutRawCause(String failureType, EmailChangeWriteException.Reason reason) {
        var accounts = mock(AccountRepository.class);
        var manager = mock(EntityManager.class);
        var account = new AccountJpaEntity(); account.setId(UUID.randomUUID());
        RuntimeException persistenceFailure = "duplicate".equals(failureType)
                ? new DataIntegrityViolationException("private server diagnostic")
                : new IllegalStateException("private server diagnostic");
        when(accounts.updateVerifiedEmail(eq(account.getId()), anyString(), any())).thenThrow(persistenceFailure);
        var failure = assertThrows(EmailChangeWriteException.class, () ->
                new EmailChangeAccountWriter(accounts, manager).updateVerifiedEmail(account, "target@example.test", Instant.now()));
        assertEquals(reason, failure.reason());
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains("private server diagnostic"));
        verify(manager, never()).detach(any());
    }
}
