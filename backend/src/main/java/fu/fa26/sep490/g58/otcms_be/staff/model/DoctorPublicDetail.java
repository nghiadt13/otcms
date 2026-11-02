package fu.fa26.sep490.g58.otcms_be.staff.model;

import java.time.LocalDate;
import java.util.List;

public record DoctorPublicDetail(DoctorProfile profile, List<AvailabilityDay> availability) {
    public DoctorPublicDetail { availability = List.copyOf(availability); }
    public record AvailabilityDay(LocalDate date, boolean hasOpenTime) { }
}
