package fu.fa26.sep490.g58.otcms_be.appointment.service;


import java.time.LocalDate;
import java.util.UUID;

public class AppointmentAvailabilityQuery {
    private final UUID serviceId;
    private final LocalDate fromDate;
    private final LocalDate toDate;
    private final UUID doctorProfileId;
    private final Audience audience;
    public AppointmentAvailabilityQuery(UUID serviceId, LocalDate fromDate, LocalDate toDate, UUID doctorProfileId, Audience audience) {
        this.serviceId = serviceId;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.doctorProfileId = doctorProfileId;
        this.audience = audience;
    }
    public UUID getServiceId() { return serviceId; }
    public UUID serviceId() { return serviceId; }
    public LocalDate getFromDate() { return fromDate; }
    public LocalDate fromDate() { return fromDate; }
    public LocalDate getToDate() { return toDate; }
    public LocalDate toDate() { return toDate; }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public UUID doctorProfileId() { return doctorProfileId; }
    public Audience getAudience() { return audience; }
    public Audience audience() { return audience; }
    public enum Audience { PUBLIC, STAFF }
}
