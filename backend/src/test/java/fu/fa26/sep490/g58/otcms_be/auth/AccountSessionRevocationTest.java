package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.model.dto.AccountAccessSnapshot;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.*;
import fu.fa26.sep490.g58.otcms_be.auth.repository.*;
import fu.fa26.sep490.g58.otcms_be.auth.service.AccountAccessService.AccessState;
import fu.fa26.sep490.g58.otcms_be.auth.service.impl.AccountAccessServiceImpl;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountSessionRevocationTest {
    @Test void revokedPatientAndOwnerSessionsCannotUseBearerTokens() {
        var accounts = mock(AccountRepository.class);
        var sessions = mock(AuthSessionRepository.class);
        var service = new AccountAccessServiceImpl(accounts, sessions);
        for (var type : new AccountType[]{AccountType.PATIENT, AccountType.OWNER}) {
            var id = UUID.randomUUID();
            var actor = new AuthenticatedAccount(id, AccountRole.valueOf(type.name()), UUID.randomUUID());
            when(accounts.findAccessSnapshotById(id)).thenReturn(Optional.of(new AccountAccessSnapshot(type, AccountStatus.ACTIVE, false)));
            when(sessions.existsByIdAndAccountIdAndRevokedAtIsNullAndExpiresAtAfter(eq(actor.sessionId()), eq(id), any())).thenReturn(false);
            assertEquals(AccessState.INVALID_SESSION, service.evaluate(actor));
        }
    }
}
