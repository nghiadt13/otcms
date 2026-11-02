package fu.fa26.sep490.g58.otcms_be.staff.service;

import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorWorkShift;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DoctorScheduleService {
    List<DoctorWorkShift> forAccount(UUID accountId, Instant from, Instant to);
    List<DoctorWorkShift> forProfile(UUID doctorProfileId, Instant from, Instant to);
}
