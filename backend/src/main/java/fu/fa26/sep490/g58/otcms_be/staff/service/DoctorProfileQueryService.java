package fu.fa26.sep490.g58.otcms_be.staff.service;

import java.util.UUID;

/** Minimal Staff-owned profile identity/existence check for other feature owners. */
public interface DoctorProfileQueryService {
    boolean existsDoctorProfile(UUID doctorProfileId);
}
