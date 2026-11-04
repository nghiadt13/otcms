package fu.fa26.sep490.g58.otcms_be.auth.service;

import java.util.Optional;
import java.util.UUID;

/** Read-only view of the signed-in account for the web shell (header name, role home). */
public interface MyAccountQueryService {
    Optional<MyAccountView> find(UUID accountId);

    /** Explicit account display name wins; legacy records retain profile/name/login fallbacks. */
    record MyAccountView(UUID id, String accountType, String email, String displayName,
            String kind, String username, String role, String avatarUrl, boolean mustChangePassword,
            String status, java.util.List<String> allowedActions) {
        public MyAccountView(UUID id, String accountType, String email, String displayName) {
            this(id, accountType, email, displayName, "PATIENT".equals(accountType) ? "PATIENT" : "STAFF",
                    null, accountType, null, false, "ACTIVE", java.util.List.of());
        }
    }
}
