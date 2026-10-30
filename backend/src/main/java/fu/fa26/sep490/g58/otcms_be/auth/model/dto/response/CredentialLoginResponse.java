package fu.fa26.sep490.g58.otcms_be.auth.model.dto.response;

import java.util.UUID;
public class CredentialLoginResponse {
    private final UUID accountId;
    private final String accountType;
    public CredentialLoginResponse(UUID accountId, String accountType) {
        this.accountId = accountId;
        this.accountType = accountType;
    }
    public UUID getAccountId() { return accountId; }
    public UUID accountId() { return accountId; }
    public String getAccountType() { return accountType; }
    public String accountType() { return accountType; }
}
