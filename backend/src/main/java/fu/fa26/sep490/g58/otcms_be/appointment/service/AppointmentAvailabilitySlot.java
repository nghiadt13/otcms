package fu.fa26.sep490.g58.otcms_be.appointment.service;


import java.time.Instant;
import java.util.UUID;

public class AppointmentAvailabilitySlot {
    private final UUID serviceId;
    private final UUID doctorId;
    private final String doctorDisplayName;
    private final Instant startTime;
    private final Instant endTime;
    private final Instant occupiedUntil;
    public AppointmentAvailabilitySlot(UUID serviceId, UUID doctorId, String doctorDisplayName, Instant startTime, Instant endTime, Instant occupiedUntil) {
        this.serviceId = serviceId;
        this.doctorId = doctorId;
        this.doctorDisplayName = doctorDisplayName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.occupiedUntil = occupiedUntil;
    }
    public UUID getServiceId() { return serviceId; }
    public UUID serviceId() { return serviceId; }
    public UUID getDoctorId() { return doctorId; }
    public UUID doctorId() { return doctorId; }
    public String getDoctorDisplayName() { return doctorDisplayName; }
    public String doctorDisplayName() { return doctorDisplayName; }
    public Instant getStartTime() { return startTime; }
    public Instant startTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public Instant endTime() { return endTime; }
    public Instant getOccupiedUntil() { return occupiedUntil; }
    public Instant occupiedUntil() { return occupiedUntil; }
}
