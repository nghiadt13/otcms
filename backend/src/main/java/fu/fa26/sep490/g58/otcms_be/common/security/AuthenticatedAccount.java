package fu.fa26.sep490.g58.otcms_be.common.security;

import java.util.UUID;

/** Authenticated identity projection shared by web features without exposing Auth internals. */
public class AuthenticatedAccount {
    private final UUID accountId;
    private final AccountRole accountRole;
    private final UUID sessionId;

    public AuthenticatedAccount(UUID accountId, AccountRole accountRole, UUID sessionId) {
        this.accountId = accountId;
        this.accountRole = accountRole;
        this.sessionId = sessionId;
    }

    public UUID accountId() { return accountId; }
    public UUID getAccountId() { return accountId; }
    public AccountRole accountRole() { return accountRole; }
    public AccountRole getAccountRole() { return accountRole; }
    public UUID sessionId() { return sessionId; }
    public UUID getSessionId() { return sessionId; }
}
