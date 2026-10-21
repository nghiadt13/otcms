package fu.fa26.sep490.g58.otcms_be.auth.model.dto.response;


import java.util.UUID;

public class SessionTokenResponse {
    private final String accessToken;
    private final String tokenType;
    private final long expiresIn;
    private final UUID accountId;
    private final String accountType;
    public SessionTokenResponse(String accessToken, String tokenType, long expiresIn, UUID accountId, String accountType) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.accountId = accountId;
        this.accountType = accountType;
    }
    public String getAccessToken() { return accessToken; }
    public String accessToken() { return accessToken; }
    public String getTokenType() { return tokenType; }
    public String tokenType() { return tokenType; }
    public long getExpiresIn() { return expiresIn; }
    public long expiresIn() { return expiresIn; }
    public UUID getAccountId() { return accountId; }
    public UUID accountId() { return accountId; }
    public String getAccountType() { return accountType; }
    public String accountType() { return accountType; }
}
