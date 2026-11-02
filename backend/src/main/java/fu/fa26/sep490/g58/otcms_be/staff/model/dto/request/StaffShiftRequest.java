package fu.fa26.sep490.g58.otcms_be.staff.model.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record StaffShiftRequest(UUID staffId, UUID roomId, LocalDate date,
        LocalTime startTime, LocalTime endTime) { }
