package fu.fa26.sep490.g58.otcms_be.staff.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.staff.exception.DoctorProfileException;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.request.DoctorIntroduction;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.StaffProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.staff.repository.StaffProfileRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnDoctorIntroductionService {
    private final StaffProfileRepository profiles;
    private final CoordinationGuardService guard;

    public OwnDoctorIntroductionService(StaffProfileRepository profiles, CoordinationGuardService guard) {
        this.profiles = profiles;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public DoctorIntroduction get(UUID accountId) {
        return introduction(requireOwnProfile(accountId));
    }

    @Transactional
    public DoctorIntroduction update(UUID accountId, DoctorIntroduction request) {
        guard.acquire();
        var profile = requireOwnProfile(accountId);
        profile.updateIntroduction(request.specialties().trim(), request.qualifications().trim(),
                request.biography().trim());
        return introduction(profile);
    }

    private StaffProfileJpaEntity requireOwnProfile(UUID accountId) {
        return profiles.findByAccountIdAndDoctorTrue(accountId)
                .filter(profile -> "ACTIVE".equals(profile.status()))
                .orElseThrow(() -> new DoctorProfileException("DOCTOR_PROFILE_NOT_FOUND"));
    }

    private DoctorIntroduction introduction(StaffProfileJpaEntity profile) {
        return new DoctorIntroduction(profile.specialties(), profile.qualifications(), profile.biography());
    }
}
