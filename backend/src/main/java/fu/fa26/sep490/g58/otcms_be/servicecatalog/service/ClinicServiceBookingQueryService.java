package fu.fa26.sep490.g58.otcms_be.servicecatalog.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Service-owned current catalog and Doctor assignment projection. */
    public interface ClinicServiceBookingQueryService {
    BookableService findActiveService(UUID serviceId, BookingAudience audience);

    boolean hasActiveDoctorAssignment(UUID serviceId, UUID doctorProfileId);

    /** Name and current price regardless of whether the Service is still active (for appointment history). */
    Optional<ServiceLabel> findServiceLabel(UUID serviceId);

    record ServiceLabel(String name, BigDecimal price) { }

        static class BookableService {
    private final UUID serviceId;
    private final String displayName;
    private final Duration consultationDuration;
    private final Duration bufferDuration;
    private final boolean onlineBookable;
    public BookableService(UUID serviceId, String displayName, Duration consultationDuration, Duration bufferDuration, boolean onlineBookable) {
        this.serviceId = serviceId;
        this.displayName = displayName;
        this.consultationDuration = consultationDuration;
        this.bufferDuration = bufferDuration;
        this.onlineBookable = onlineBookable;
    }
    public UUID getServiceId() { return serviceId; }
    public UUID serviceId() { return serviceId; }
    public String getDisplayName() { return displayName; }
    public String displayName() { return displayName; }
    public Duration getConsultationDuration() { return consultationDuration; }
    public Duration consultationDuration() { return consultationDuration; }
    public Duration getBufferDuration() { return bufferDuration; }
    public Duration bufferDuration() { return bufferDuration; }
    public boolean getOnlineBookable() { return onlineBookable; }
    public boolean isOnlineBookable() { return onlineBookable; }
    public boolean onlineBookable() { return onlineBookable; }
}

    enum BookingAudience { PUBLIC_PATIENT, RECEPTIONIST, DOCTOR_FOLLOW_UP }
}
