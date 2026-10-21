package fu.fa26.sep490.g58.otcms_be.appointment.model;


import fu.fa26.sep490.g58.otcms_be.appointment.model.SlotServiceContext;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public class SlotAvailabilityRequest {
    private final Set<UUID> doctorProfileIds;
    private final LocalDate fromDate;
    private final LocalDate toDate;
    private final SlotServiceContext serviceContext;
    public SlotAvailabilityRequest(Set<UUID> doctorProfileIds, LocalDate fromDate, LocalDate toDate, SlotServiceContext serviceContext) {
        if (doctorProfileIds == null || doctorProfileIds.stream().anyMatch(id -> id == null)
                || fromDate == null || toDate == null || serviceContext == null) {
            throw new IllegalArgumentException("Availability request fields are required");
        }
        doctorProfileIds = Set.copyOf(doctorProfileIds);
        this.doctorProfileIds = doctorProfileIds;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.serviceContext = serviceContext;
    }
    public Set<UUID> getDoctorProfileIds() { return doctorProfileIds; }
    public Set<UUID> doctorProfileIds() { return doctorProfileIds; }
    public LocalDate getFromDate() { return fromDate; }
    public LocalDate fromDate() { return fromDate; }
    public LocalDate getToDate() { return toDate; }
    public LocalDate toDate() { return toDate; }
    public SlotServiceContext getServiceContext() { return serviceContext; }
    public SlotServiceContext serviceContext() { return serviceContext; }
}
