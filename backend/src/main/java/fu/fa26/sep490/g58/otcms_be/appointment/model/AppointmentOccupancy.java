package fu.fa26.sep490.g58.otcms_be.appointment.model;


import java.util.UUID;

public class AppointmentOccupancy {
    private final UUID appointmentId;
    private final UUID doctorProfileId;
    private final AvailabilityInterval interval;
    public AppointmentOccupancy(UUID appointmentId, UUID doctorProfileId, AvailabilityInterval interval) {
        this.appointmentId = appointmentId;
        this.doctorProfileId = doctorProfileId;
        this.interval = interval;
    }
    public UUID getAppointmentId() { return appointmentId; }
    public UUID appointmentId() { return appointmentId; }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public UUID doctorProfileId() { return doctorProfileId; }
    public AvailabilityInterval getInterval() { return interval; }
    public AvailabilityInterval interval() { return interval; }
}
