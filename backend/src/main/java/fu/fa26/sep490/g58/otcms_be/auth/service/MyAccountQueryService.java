package fu.fa26.sep490.g58.otcms_be.auth.service;

import java.util.Optional;
import java.util.UUID;

/** Read-only view of the signed-in account for the web shell (header name, role home). */
public interface MyAccountQueryService {
    Optional<MyAccountView> find(UUID accountId);

    /** {@code displayName} is the patient profile name, falling back to the email local part. */
    record MyAccountView(UUID id, String accountType, String email, String displayName) { }
}
