package fu.fa26.sep490.g58.otcms_be.auth.model.dto.response;

import java.util.UUID;
import lombok.Getter;

@Getter
public class ReissueAdministratorPasswordResponse {
    private final UUID accountId;
    private final String temporaryPassword;
    private final boolean mustChangePassword = true;

    public ReissueAdministratorPasswordResponse(UUID accountId, String temporaryPassword) {
        this.accountId = accountId;
        this.temporaryPassword = temporaryPassword;
    }

    @Override
    public String toString() {
        return "ReissueAdministratorPasswordResponse[credentials=REDACTED]";
    }
}
