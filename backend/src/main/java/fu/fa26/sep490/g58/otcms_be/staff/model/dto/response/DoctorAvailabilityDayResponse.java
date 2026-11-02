package fu.fa26.sep490.g58.otcms_be.staff.model.dto.response;

import java.time.LocalDate;

public record DoctorAvailabilityDayResponse(LocalDate date, boolean hasOpenTime) { }
