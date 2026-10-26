package fu.fa26.sep490.g58.otcms_be.auth.model.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Explicit read allowlist: no password, session, challenge, lockout or credential-change fields. */
public record AdministratorAccountResponse(UUID accountId, String username, String email,
        String accountType, String status, Instant createdAt, Instant updatedAt) { }
