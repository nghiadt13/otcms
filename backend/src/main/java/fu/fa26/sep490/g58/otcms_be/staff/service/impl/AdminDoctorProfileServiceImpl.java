package fu.fa26.sep490.g58.otcms_be.staff.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountTypeQueryService;
import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountTypeQueryService.DoctorAccountOption;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.staff.exception.DoctorProfileException;
import fu.fa26.sep490.g58.otcms_be.staff.mapper.DoctorProfileMapper;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorProfile;
import fu.fa26.sep490.g58.otcms_be.staff.repository.StaffProfileRepository;
import fu.fa26.sep490.g58.otcms_be.staff.service.AdminDoctorProfileService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDoctorProfileServiceImpl implements AdminDoctorProfileService {
    private final StaffProfileRepository profiles;
    private final DoctorAccountTypeQueryService accounts;
    private final CoordinationGuardService guard;

    public AdminDoctorProfileServiceImpl(StaffProfileRepository profiles, DoctorAccountTypeQueryService accounts,
            CoordinationGuardService guard) {
        this.profiles = profiles; this.accounts = accounts; this.guard = guard;
    }

    @Override @Transactional(readOnly = true)
    public List<DoctorProfile> list() {
        return profiles.findAllDoctors().stream().map(DoctorProfileMapper::toModel).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<DoctorAccountOption> eligibleAccounts() {
        var linked = profiles.findAllDoctors().stream().map(p -> p.accountId()).collect(java.util.stream.Collectors.toSet());
        return accounts.listActiveDoctorAccounts().stream().filter(a -> !linked.contains(a.accountId())).toList();
    }

    @Override @Transactional
    public DoctorProfile update(UUID doctorId, ProfileEdit edit) {
        if (edit == null || edit.fullName() == null || edit.fullName().isBlank())
            throw new DoctorProfileException("DOCTOR_PROFILE_INVALID");
        guard.acquire();
        var profile = profiles.findByIdAndDoctorTrue(doctorId)
                .orElseThrow(() -> new DoctorProfileException("DOCTOR_PROFILE_NOT_FOUND"));
        profile.updateDoctor(edit.fullName().trim(), clean(edit.photoUrl()), clean(edit.workContact()),
                clean(edit.specialties()), clean(edit.qualifications()), clean(edit.biography()), clean(edit.consultationInformation()));
        return DoctorProfileMapper.toModel(profiles.save(profile));
    }

    private String clean(String text) { return text == null ? "" : text.trim(); }
}
