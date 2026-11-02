package fu.fa26.sep490.g58.otcms_be.staff.service;

import java.util.Optional;
import java.util.UUID;

/** Staff-owned identity projection for resolving an authenticated Doctor account to its profile. */
public interface DoctorProfileIdentityQueryService {
    Optional<UUID> findDoctorProfileId(UUID accountId);
}
