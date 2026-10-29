package fu.fa26.sep490.g58.otcms_be.auth.model.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Safe account-directory fields only; patient profile edits remain in patient self-service. */
public record PatientAccountAdminResponse(UUID id, String accountType, String username, String email,
        String fullName, String status, Instant createdAt, List<String> allowedActions,
        StaffProfileSummary staffProfile) {
    public record StaffProfileSummary(String fullName, String role, String workContact) { }
}
