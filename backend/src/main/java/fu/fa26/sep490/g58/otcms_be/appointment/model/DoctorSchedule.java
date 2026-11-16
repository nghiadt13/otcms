package fu.fa26.sep490.g58.otcms_be.appointment.model;


import java.util.List;
import java.util.UUID;

public class DoctorSchedule {
    private final UUID doctorProfileId;
    private final List<AvailabilityInterval> shifts;
    public DoctorSchedule(UUID doctorProfileId, List<AvailabilityInterval> shifts) {
        if (doctorProfileId == null) throw new IllegalArgumentException("Doctor profile ID is required");
        shifts = List.copyOf(shifts);
        this.doctorProfileId = doctorProfileId;
        this.shifts = shifts;
    }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public UUID doctorProfileId() { return doctorProfileId; }
    public List<AvailabilityInterval> getShifts() { return shifts; }
    public List<AvailabilityInterval> shifts() { return shifts; }
}
