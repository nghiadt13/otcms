package fu.fa26.sep490.g58.otcms_be.staff.service.impl;

import fu.fa26.sep490.g58.otcms_be.staff.exception.DoctorProfileException;
import fu.fa26.sep490.g58.otcms_be.staff.mapper.DoctorProfileMapper;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorProfile;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorWorkShift;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorProfileRepository;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorWorkShiftRepository;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorScheduleService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorScheduleServiceImpl implements DoctorScheduleService {
    private static final Duration MAX_RANGE = Duration.ofDays(90);
    private final DoctorProfileRepository profiles;
    private final DoctorWorkShiftRepository shifts;

    public DoctorScheduleServiceImpl(DoctorProfileRepository profiles, DoctorWorkShiftRepository shifts) {
        this.profiles = profiles;
        this.shifts = shifts;
    }

    @Transactional(readOnly = true)
    public List<DoctorWorkShift> forAccount(UUID accountId, Instant from, Instant to) {
        DoctorProfile profile = profiles.findByAccountId(accountId).map(DoctorProfileMapper::toModel)
                .orElseThrow(() -> new DoctorProfileException("DOCTOR_PROFILE_NOT_FOUND"));
        return forProfile(profile.id(), from, to);
    }

    @Transactional(readOnly = true)
    public List<DoctorWorkShift> forProfile(UUID doctorProfileId, Instant from, Instant to) {
        validateRange(from, to);
        if (profiles.findById(doctorProfileId).isEmpty()) {
            throw new DoctorProfileException("DOCTOR_PROFILE_NOT_FOUND");
        }
        return shifts.findInRange(doctorProfileId, from, to).stream()
                .map(shift -> new DoctorWorkShift(shift.id(), shift.doctorProfileId(), shift.startsAt(), shift.endsAt()))
                .toList();
    }

    private void validateRange(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to) || Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new DoctorProfileException("DOCTOR_SCHEDULE_RANGE_INVALID");
        }
    }
}
