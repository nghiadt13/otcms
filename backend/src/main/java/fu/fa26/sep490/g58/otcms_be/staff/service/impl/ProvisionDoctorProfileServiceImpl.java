package fu.fa26.sep490.g58.otcms_be.staff.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountTypeQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.exception.DoctorProfileException;
import fu.fa26.sep490.g58.otcms_be.staff.mapper.DoctorProfileMapper;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorProfile;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.StaffProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.staff.repository.StaffProfileRepository;
import fu.fa26.sep490.g58.otcms_be.staff.service.ProvisionDoctorProfileService.Command;
import fu.fa26.sep490.g58.otcms_be.staff.service.ProvisionDoctorProfileService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProvisionDoctorProfileServiceImpl implements ProvisionDoctorProfileService {
    private final StaffProfileRepository profiles;
    private final CoordinationGuardService coordinationGuard;
    private final DoctorAccountTypeQueryService doctorAccountTypes;

    public ProvisionDoctorProfileServiceImpl(StaffProfileRepository profiles, CoordinationGuardService coordinationGuard,
                                             DoctorAccountTypeQueryService doctorAccountTypes) {
        this.profiles = profiles;
        this.coordinationGuard = coordinationGuard;
        this.doctorAccountTypes = doctorAccountTypes;
    }

    @Override
    @Transactional
    public DoctorProfile provision(Command command) {
        if (command.accountId() == null || command.fullName() == null || command.fullName().isBlank()) {
            throw new DoctorProfileException("DOCTOR_PROFILE_INVALID");
        }
        coordinationGuard.acquire();
        if (!doctorAccountTypes.isDoctorAccount(command.accountId())) {
            throw new DoctorProfileException("DOCTOR_ACCOUNT_UNAVAILABLE");
        }
        if (profiles.existsByAccountIdAndDoctorTrue(command.accountId())) {
            throw new DoctorProfileException("DOCTOR_ACCOUNT_ALREADY_PROFILED");
        }
        // The account may already have a non-doctor staff profile: turn that row into a doctor instead of inserting
        // a second row (staff_profiles.account_id is unique).
        StaffProfileJpaEntity existing = profiles.findByAccountId(command.accountId()).orElse(null);
        if (existing != null) {
            existing.becomeDoctor(value(command.specialties()), value(command.qualifications()),
                    value(command.biography()), value(command.consultationInformation()), false);
            existing.updateDoctor(command.fullName().trim(), command.photoUrl(), command.workContact(),
                    existing.specialties(), existing.qualifications(), existing.biography(),
                    existing.consultationInformation());
            return DoctorProfileMapper.toModel(profiles.save(existing));
        }
        DoctorProfile profile = new DoctorProfile(UUID.randomUUID(), command.accountId(), command.fullName().trim(),
                command.photoUrl(), command.workContact(), value(command.specialties()), value(command.qualifications()),
                value(command.biography()), value(command.consultationInformation()), false, false, true);
        return DoctorProfileMapper.toModel(profiles.save(DoctorProfileMapper.toEntity(profile)));
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
