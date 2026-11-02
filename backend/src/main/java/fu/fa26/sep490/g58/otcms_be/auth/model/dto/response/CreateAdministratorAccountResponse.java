package fu.fa26.sep490.g58.otcms_be.auth.model.dto.response;

import java.util.UUID;
import lombok.Getter;

/** The issued password exists only in this create result, never in account/session DTOs. */
@Getter
public class CreateAdministratorAccountResponse {
    private final UUID accountId;
    private final String username;
    private final String accountType = "ADMIN";
    private final boolean mustChangePassword = true;
    private final String temporaryPassword;

    public CreateAdministratorAccountResponse(UUID accountId, String username, String temporaryPassword) {
        this.accountId = accountId;
        this.username = username;
        this.temporaryPassword = temporaryPassword;
    }

    @Override
    public String toString() {
        return "CreateAdministratorAccountResponse[credentials=REDACTED]";
    }
}
