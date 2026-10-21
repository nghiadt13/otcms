package fu.fa26.sep490.g58.otcms_be.auth.service;

import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.SessionTokenResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;

import java.util.Optional;
import java.util.UUID;

public interface AuthSessionService {
    IssuedSession create(UUID accountId, String clientIp, String userAgent);

    IssuedSession rotate(String refreshToken);

    void logout(String refreshToken);

    void revokeAll(UUID accountId);

    Optional<AccountType> accountTypeForRefresh(String refreshToken);

    final class IssuedSession {
        private final SessionTokenResponse body;
        private final String refreshToken;

        public IssuedSession(SessionTokenResponse body, String refreshToken) {
            this.body = body;
            this.refreshToken = refreshToken;
        }

        public SessionTokenResponse getBody() {
            return body;
        }

        public SessionTokenResponse body() {
            return body;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public String refreshToken() {
            return refreshToken;
        }
    }
}
