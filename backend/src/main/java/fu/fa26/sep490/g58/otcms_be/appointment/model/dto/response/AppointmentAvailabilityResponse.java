package fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response;


import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class AppointmentAvailabilityResponse {
    private final List<Slot> slots;
    public AppointmentAvailabilityResponse(List<Slot> slots) {
        this.slots = slots;
    }
    public List<Slot> getSlots() { return slots; }
    public List<Slot> slots() { return slots; }
    public static class Slot {
    private final UUID serviceId;
    private final UUID doctorId;
    private final String doctorDisplayName;
    private final Instant startTime;
    private final Instant endTime;
    private final Instant occupiedUntil;
    public Slot(UUID serviceId, UUID doctorId, String doctorDisplayName, Instant startTime, Instant endTime, Instant occupiedUntil) {
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
}
