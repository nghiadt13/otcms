package fu.fa26.sep490.g58.otcms_be.auth.dto;

import java.util.UUID;

public record SessionTokenResponse(String accessToken, String tokenType, long expiresIn, UUID accountId, String accountType) {}
