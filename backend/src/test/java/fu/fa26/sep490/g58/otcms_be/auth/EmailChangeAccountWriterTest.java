package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.exception.EmailChangeWriteException;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.repository.EmailChangeAccountWriter;
import jakarta.persistence.EntityManager;
import java.sql.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailChangeAccountWriterTest {
    @org.junit.jupiter.api.Test
    void missingRowDoesNotRetainStatementCloseDiagnostic() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var manager = mock(EntityManager.class);
        var connection = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        var account = new AccountJpaEntity(); account.setId(UUID.randomUUID());
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenReturn(0);
        doThrow(new SQLException("private close diagnostic", "08006")).when(statement).close();
        doAnswer(invocation -> ((ConnectionCallback<?>) invocation.getArgument(0)).doInConnection(connection))
                .when(jdbc).execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Object>>any());
        var failure = assertThrows(EmailChangeWriteException.class, () ->
                new EmailChangeAccountWriter(jdbc, manager).updateVerifiedEmail(account, "target@example.test", Instant.now()));
        assertEquals(EmailChangeWriteException.Reason.OPERATION_FAILED, failure.reason());
        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
    }

    @ParameterizedTest @CsvSource({"23505,COLLISION", "23514,OPERATION_FAILED", "08006,OPERATION_FAILED"})
    void sqlFailuresHaveNoRawCauseOrDiagnostic(String state, EmailChangeWriteException.Reason reason) throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var manager = mock(EntityManager.class);
        var connection = mock(Connection.class);
        var statement = mock(PreparedStatement.class);
        var account = new AccountJpaEntity(); account.setId(UUID.randomUUID());
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeUpdate()).thenThrow(new SQLException("private server diagnostic", state));
        doAnswer(invocation -> ((ConnectionCallback<?>) invocation.getArgument(0)).doInConnection(connection))
                .when(jdbc).execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Object>>any());
        var failure = assertThrows(EmailChangeWriteException.class, () ->
                new EmailChangeAccountWriter(jdbc, manager).updateVerifiedEmail(account, "target@example.test", Instant.now()));
        assertEquals(reason, failure.reason());
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains("private server diagnostic"));
        verify(manager, never()).detach(any());
    }
}
