package fu.fa26.sep490.g58.otcms_be.auth.service;

import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.SessionTokenResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;

import java.util.Optional;
import java.util.UUID;

public interface AuthSessionService {
    IssuedSession create(UUID accountId, String clientIp, String userAgent, boolean rememberMe);

    /** Đăng nhập không có lựa chọn ghi nhớ giữ hành vi cũ: ghi nhớ. */
    default IssuedSession create(UUID accountId, String clientIp, String userAgent) {
        return create(accountId, clientIp, userAgent, true);
    }

    IssuedSession rotate(String refreshToken);

    void logout(String refreshToken);

    void revokeAll(UUID accountId);
    void revokeAll(UUID accountId, String reason);

    Optional<AccountType> accountTypeForRefresh(String refreshToken);

    final class IssuedSession {
        private final SessionTokenResponse body;
        private final String refreshToken;
        private final boolean rememberMe;

        public IssuedSession(SessionTokenResponse body, String refreshToken) {
            this(body, refreshToken, true);
        }

        public IssuedSession(SessionTokenResponse body, String refreshToken, boolean rememberMe) {
            this.body = body;
            this.refreshToken = refreshToken;
            this.rememberMe = rememberMe;
        }

        public boolean rememberMe() {
            return rememberMe;
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
