package fu.fa26.sep490.g58.otcms_be.appointment.model;


import java.time.Duration;
import java.util.UUID;

/** Immutable service timing selected from the current catalog or an existing appointment snapshot. */
public class SlotServiceContext {
    private final UUID serviceId;
    private final Duration consultationDuration;
    private final Duration bufferDuration;
    private final Source source;
    private final UUID excludedAppointmentId;
    private final UUID patientProfileId;
    public SlotServiceContext(UUID serviceId, Duration consultationDuration, Duration bufferDuration, Source source, UUID excludedAppointmentId, UUID patientProfileId) {
        if (serviceId == null || consultationDuration == null || consultationDuration.isZero()
                || consultationDuration.isNegative() || bufferDuration == null || bufferDuration.isNegative()
                || source == null || !wholeMinutes(consultationDuration) || !wholeMinutes(bufferDuration)) {
            throw new IllegalArgumentException("Invalid service slot context");
        }
        if (source == Source.CURRENT_SERVICE && (excludedAppointmentId != null || patientProfileId != null)) {
            throw new IllegalArgumentException("Current-service context cannot exclude an appointment");
        }
        if (source == Source.APPOINTMENT_SNAPSHOT
                && (excludedAppointmentId == null || patientProfileId == null)) {
            throw new IllegalArgumentException("Appointment snapshot context requires appointment and patient IDs");
        }
        this.serviceId = serviceId;
        this.consultationDuration = consultationDuration;
        this.bufferDuration = bufferDuration;
        this.source = source;
        this.excludedAppointmentId = excludedAppointmentId;
        this.patientProfileId = patientProfileId;
    }
    public UUID getServiceId() { return serviceId; }
    public UUID serviceId() { return serviceId; }
    public Duration getConsultationDuration() { return consultationDuration; }
    public Duration consultationDuration() { return consultationDuration; }
    public Duration getBufferDuration() { return bufferDuration; }
    public Duration bufferDuration() { return bufferDuration; }
    public Source getSource() { return source; }
    public Source source() { return source; }
    public UUID getExcludedAppointmentId() { return excludedAppointmentId; }
    public UUID excludedAppointmentId() { return excludedAppointmentId; }
    public UUID getPatientProfileId() { return patientProfileId; }
    public UUID patientProfileId() { return patientProfileId; }
    public static SlotServiceContext current(UUID serviceId, Duration consultationDuration, Duration bufferDuration) {
        return new SlotServiceContext(serviceId, consultationDuration, bufferDuration,
                Source.CURRENT_SERVICE, null, null);
    }

    public static SlotServiceContext snapshot(UUID serviceId, int durationMinutes, int bufferMinutes,
                                              UUID appointmentId, UUID patientProfileId) {
        if (durationMinutes < 1 || bufferMinutes < 0) {
            throw new IllegalArgumentException("Appointment snapshots must contain valid minute values");
        }
        return new SlotServiceContext(serviceId, Duration.ofMinutes(durationMinutes),
                Duration.ofMinutes(bufferMinutes), Source.APPOINTMENT_SNAPSHOT, appointmentId, patientProfileId);
    }

    public Duration occupiedDuration() {
        return consultationDuration.plus(bufferDuration);
    }

    private static boolean wholeMinutes(Duration duration) {
        return duration.getNano() == 0 && duration.getSeconds() % 60 == 0;
    }

    public enum Source { CURRENT_SERVICE, APPOINTMENT_SNAPSHOT }
}
