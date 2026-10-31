package fu.fa26.sep490.g58.otcms_be.auth.model.dto;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountStatus;
import java.time.Instant;
import java.util.UUID;

/** Read projection for the Admin patient-account directory; contains no credentials or profile fields. */
public record PatientAccountAdminRow(UUID id, String email, String fullName, AccountStatus status,
        Instant createdAt) { }
