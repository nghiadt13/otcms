package fu.fa26.sep490.g58.otcms_be.staff.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountTypeQueryService;
import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountTypeQueryService.DoctorAccountOption;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.staff.exception.DoctorProfileException;
import fu.fa26.sep490.g58.otcms_be.staff.mapper.DoctorProfileMapper;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorProfile;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.DoctorIntervalResponse;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorProfileRepository;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorOperationalTimeOffRepository;
import fu.fa26.sep490.g58.otcms_be.staff.service.AdminDoctorProfileService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDoctorProfileServiceImpl implements AdminDoctorProfileService {
    private final DoctorProfileRepository profiles;
    private final DoctorOperationalTimeOffRepository timeOffs;
    private final DoctorAccountTypeQueryService accounts;
    private final CoordinationGuardService guard;

    public AdminDoctorProfileServiceImpl(DoctorProfileRepository profiles,
            DoctorOperationalTimeOffRepository timeOffs, DoctorAccountTypeQueryService accounts,
            CoordinationGuardService guard) {
        this.profiles = profiles; this.timeOffs = timeOffs; this.accounts = accounts; this.guard = guard;
    }

    @Override @Transactional(readOnly = true)
    public List<DoctorProfile> list() {
        return profiles.findAll(Sort.by("fullName", "id")).stream().map(DoctorProfileMapper::toModel).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<DoctorAccountOption> eligibleAccounts() {
        var linked = profiles.findAll().stream().map(p -> p.accountId()).collect(java.util.stream.Collectors.toSet());
        return accounts.listActiveDoctorAccounts().stream().filter(a -> !linked.contains(a.accountId())).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<DoctorIntervalResponse> timeOffs(UUID doctorId, Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to))
            throw new DoctorProfileException("DOCTOR_RESOURCE_REQUEST_INVALID");
        if (!profiles.existsById(doctorId)) throw new DoctorProfileException("DOCTOR_PROFILE_NOT_FOUND");
        return timeOffs.findOverlapping(List.of(doctorId), from, to).stream()
                .map(t -> new DoctorIntervalResponse(t.id(), t.doctorProfileId(), t.startsAt(), t.endsAt())).toList();
    }

    @Override @Transactional
    public DoctorProfile update(UUID doctorId, ProfileEdit edit) {
        if (edit == null || edit.fullName() == null || edit.fullName().isBlank())
            throw new DoctorProfileException("DOCTOR_PROFILE_INVALID");
        guard.acquire();
        var profile = profiles.findById(doctorId).orElseThrow(() -> new DoctorProfileException("DOCTOR_PROFILE_NOT_FOUND"));
        profile.updateDetails(edit.fullName().trim(), clean(edit.photoUrl()), clean(edit.workContact()),
                clean(edit.specialties()), clean(edit.qualifications()), clean(edit.biography()), clean(edit.consultationInformation()));
        return DoctorProfileMapper.toModel(profiles.save(profile));
    }

    private String clean(String text) { return text == null ? "" : text.trim(); }
}
