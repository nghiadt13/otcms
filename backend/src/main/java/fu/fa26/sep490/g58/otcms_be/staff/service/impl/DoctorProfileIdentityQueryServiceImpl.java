package fu.fa26.sep490.g58.otcms_be.staff.service.impl;

import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorProfileRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class DoctorProfileIdentityQueryServiceImpl implements DoctorProfileIdentityQueryService {
    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.Map<UUID, String> findDoctorNames(java.util.Collection<UUID> profileIds) {
        return profiles.findAllById(profileIds).stream().collect(java.util.stream.Collectors.toMap(
                profile -> profile.id(), profile -> profile.fullName()));
    }
    private final DoctorProfileRepository profiles;

    public DoctorProfileIdentityQueryServiceImpl(DoctorProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Override
    public Optional<UUID> findDoctorProfileId(UUID accountId) {
        if (accountId == null) return Optional.empty();
        return profiles.findByAccountId(accountId).map(profile -> profile.id());
    }

    @Override
    public Optional<UUID> findDoctorAccountId(UUID doctorProfileId) {
        if (doctorProfileId == null) return Optional.empty();
        return profiles.findById(doctorProfileId).map(profile -> profile.accountId());
    }
}
