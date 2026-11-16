package fu.fa26.sep490.g58.otcms_be.staff.service.impl;

import fu.fa26.sep490.g58.otcms_be.staff.repository.StaffProfileRepository;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Doctor identity lookups over staff_profiles; every query filters is_doctor so other staff never match. */
@Service
public class DoctorProfileIdentityQueryServiceImpl implements DoctorProfileIdentityQueryService {
    private final StaffProfileRepository profiles;

    public DoctorProfileIdentityQueryServiceImpl(StaffProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> findDoctorNames(Collection<UUID> profileIds) {
        return profiles.findAllByIdInAndDoctorTrue(profileIds).stream().collect(Collectors.toMap(
                profile -> profile.id(), profile -> profile.displayName()));
    }

    @Override
    public Optional<UUID> findDoctorProfileId(UUID accountId) {
        if (accountId == null) return Optional.empty();
        return profiles.findByAccountIdAndDoctorTrue(accountId).map(profile -> profile.id());
    }

    @Override
    public Optional<UUID> findDoctorAccountId(UUID doctorProfileId) {
        if (doctorProfileId == null) return Optional.empty();
        return profiles.findByIdAndDoctorTrue(doctorProfileId).map(profile -> profile.accountId());
    }
}
