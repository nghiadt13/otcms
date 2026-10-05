package fu.fa26.sep490.g58.otcms_be.auth.dto;
import java.util.UUID;
public record CredentialLoginResponse(UUID accountId, String accountType) {}
