package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CheckedInAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentCheckInRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CheckInAppointmentCommand;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.ReceptionWorkflowException;
import fu.fa26.sep490.g58.otcms_be.visit.service.ReceptionWorkflowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
public class CheckInAppointmentTransaction {
    private static final String REQUEST_FINGERPRINT = fingerprint("appointment-check-in-v1:{}");

    private final CoordinationGuardService guard;
    private final AppointmentCheckInRepository appointments;
    private final ReceptionWorkflowService reception;
    private final AppointmentBookingProperties properties;

    public CheckInAppointmentTransaction(CoordinationGuardService guard, AppointmentCheckInRepository appointments,
            ReceptionWorkflowService reception, AppointmentBookingProperties properties) {
        this.guard = guard;
        this.appointments = appointments;
        this.reception = reception;
        this.properties = properties;
    }

    @Transactional
    public CheckedInAppointment checkIn(UUID appointmentId, CheckInAppointmentCommand command) {
        if (appointmentId == null || command == null || command.actorAccountId() == null
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.idempotencyKey().trim().length() > 128 || command.correlationId() == null
                || command.correlationId().isBlank() || command.correlationId().length() > 128) {
            throw new AppointmentBookingException("VALIDATION_FAILED");
        }
        String key = command.idempotencyKey().trim();
        guard.acquire();

        // A successful authorized replay takes precedence over current lifecycle and time-window state.
        var replay = appointments.findReplay(command.actorAccountId(), appointmentId, key);
        if (replay.isPresent()) {
            var stored = replay.get();
            if (!REQUEST_FINGERPRINT.equalsIgnoreCase(stored.requestFingerprint())) {
                throw new AppointmentBookingException("IDEMPOTENCY_CONFLICT");
            }
            return new CheckedInAppointment(appointmentId, stored.visitId(), stored.checkedInAt());
        }

        var current = appointments.findForUpdate(appointmentId)
                .orElseThrow(() -> new AppointmentBookingException("APPOINTMENT_NOT_FOUND"));
        if (current.visitId() != null) {
            if (!reception.hasConsistentCheckInEvidence(appointmentId, current.patientProfileId(), current.visitId())) {
                throw new AppointmentBookingException("VISIT_LINK_CONFLICT");
            }
            if (!"CONFIRMED".equals(current.status())) {
                throw new AppointmentBookingException("APPOINTMENT_NOT_CHANGEABLE");
            }
            throw new AppointmentBookingException("VISIT_LINK_CONFLICT");
        }
        if (!"CONFIRMED".equals(current.status())) {
            if (Set.of("CHECKED_IN", "IN_PROGRESS", "COMPLETED").contains(current.status())) {
                throw new AppointmentBookingException("VISIT_LINK_CONFLICT");
            }
            throw new AppointmentBookingException("APPOINTMENT_NOT_CHANGEABLE");
        }

        Instant allowedStart = current.startTime().minus(properties.checkInEarlyMinutes(), ChronoUnit.MINUTES);
        Instant allowedEnd = current.startTime().plus(properties.checkInLateGraceMinutes(), ChronoUnit.MINUTES);

        ReceptionWorkflowService.CheckInResult receptionResult;
        try {
            receptionResult = reception.checkInAtomically(new ReceptionWorkflowService.CheckInCommand(
                    command.actorAccountId(), appointmentId, current.patientProfileId(), key, allowedStart, allowedEnd));
        } catch (ReceptionWorkflowException exception) {
            throw new AppointmentBookingException(exception.code(), exception);
        }
        if (receptionResult == null || !appointmentId.equals(receptionResult.appointmentId())
                || !current.patientProfileId().equals(receptionResult.patientProfileId())
                || receptionResult.visitId() == null || receptionResult.checkedInAt() == null) {
            throw new AppointmentBookingException("VISIT_LINK_CONFLICT");
        }
        Instant checkedInAt = receptionResult.checkedInAt();
        if (checkedInAt.isBefore(allowedStart) || checkedInAt.isAfter(allowedEnd)) {
            throw new AppointmentBookingException("CHECK_IN_TIME_NOT_ALLOWED");
        }
        return appointments.completeCheckIn(current, command.actorAccountId(), key, REQUEST_FINGERPRINT,
                receptionResult.visitId(), checkedInAt, command.correlationId());
    }

    private static String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }
}
