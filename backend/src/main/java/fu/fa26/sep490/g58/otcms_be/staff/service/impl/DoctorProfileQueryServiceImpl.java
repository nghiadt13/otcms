package fu.fa26.sep490.g58.otcms_be.staff.service.impl;

import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorProfileRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DoctorProfileQueryServiceImpl implements DoctorProfileQueryService {
    private final DoctorProfileRepository profiles;
    public DoctorProfileQueryServiceImpl(DoctorProfileRepository profiles) { this.profiles=profiles; }
    @Override public boolean existsDoctorProfile(UUID doctorProfileId) {
        return doctorProfileId != null && profiles.existsById(doctorProfileId);
    }
}
