package fu.fa26.sep490.g58.otcms_be.staff.service.impl;

import fu.fa26.sep490.g58.otcms_be.staff.mapper.DoctorProfileMapper;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorProfile;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorProfileRepository;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorDirectoryService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorDirectoryServiceImpl implements DoctorDirectoryService {
    private final DoctorProfileRepository profiles;

    public DoctorDirectoryServiceImpl(DoctorProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public List<DoctorProfile> listPublic() {
        return profiles.findPublicDirectory().stream().map(DoctorProfileMapper::toModel).toList();
    }

    @Transactional(readOnly = true)
    public Optional<DoctorProfile> findPublic(UUID id) {
        return profiles.findById(id).filter(profile -> profile.publiclyVisible() && profile.inActiveDirectory())
                .map(DoctorProfileMapper::toModel);
    }
}
