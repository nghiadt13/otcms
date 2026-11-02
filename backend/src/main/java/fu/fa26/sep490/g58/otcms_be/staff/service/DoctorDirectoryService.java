package fu.fa26.sep490.g58.otcms_be.staff.service;

import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctorDirectoryService {
    List<DoctorProfile> listPublic();
    Optional<DoctorProfile> findPublic(UUID id);
}
