package fu.fa26.sep490.g58.otcms_be.appointment.model;


import java.time.Instant;
import java.util.UUID;

public class AvailableSlot {
    private final UUID serviceId;
    private final UUID doctorProfileId;
    private final Instant startTime;
    private final Instant endTime;
    private final Instant occupiedUntil;
    public AvailableSlot(UUID serviceId, UUID doctorProfileId, Instant startTime, Instant endTime, Instant occupiedUntil) {
        this.serviceId = serviceId;
        this.doctorProfileId = doctorProfileId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.occupiedUntil = occupiedUntil;
    }
    public UUID getServiceId() { return serviceId; }
    public UUID serviceId() { return serviceId; }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public UUID doctorProfileId() { return doctorProfileId; }
    public Instant getStartTime() { return startTime; }
    public Instant startTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public Instant endTime() { return endTime; }
    public Instant getOccupiedUntil() { return occupiedUntil; }
    public Instant occupiedUntil() { return occupiedUntil; }
}
