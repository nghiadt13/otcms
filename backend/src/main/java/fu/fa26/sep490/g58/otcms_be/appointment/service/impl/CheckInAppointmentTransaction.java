package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.mapper.AppointmentAuditEntries;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.idempotency.IdempotencyScope;
import fu.fa26.sep490.g58.otcms_be.common.idempotency.IdempotencyStore;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentQueryException;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CheckedInAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentCheckInRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentBillingSourceService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CheckInAppointmentCommand;
import fu.fa26.sep490.g58.otcms_be.billing.service.AppointmentInvoicePaidStatusQueryService;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.ReceptionWorkflowException;
import fu.fa26.sep490.g58.otcms_be.visit.service.ReceptionWorkflowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** @ears REQ-BOOK-055 REQ-RECEPTION-002 REQ-RECEPTION-003 REQ-RECEPTION-010 */
@Service
public class CheckInAppointmentTransaction {
    private final AuditLogWriter auditLogWriter;
    private static final String FINGERPRINT_PREFIX = "appointment-check-in-v2:";

    private final CoordinationGuardService guard;
    private final AppointmentCheckInRepository appointments;
    private final IdempotencyStore idempotency;
    private final ReceptionWorkflowService reception;
    private final AppointmentBillingSourceService billingSources;
    private final AppointmentInvoicePaidStatusQueryService invoicePaidStatus;
    private final AppointmentBookingProperties properties;
    private final Clock clock;

    public CheckInAppointmentTransaction(CoordinationGuardService guard, AppointmentCheckInRepository appointments,
            IdempotencyStore idempotency, ReceptionWorkflowService reception, AppointmentBillingSourceService billingSources,
            AppointmentInvoicePaidStatusQueryService invoicePaidStatus, AppointmentBookingProperties properties,
            Clock clock, AuditLogWriter auditLogWriter) {
        this.auditLogWriter = auditLogWriter;
        this.guard = guard;
        this.appointments = appointments;
        this.idempotency = idempotency;
        this.reception = reception;
        this.billingSources = billingSources;
        this.invoicePaidStatus = invoicePaidStatus;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public CheckedInAppointment checkIn(UUID appointmentId, CheckInAppointmentCommand command) {
        if (appointmentId == null || command == null || command.actorAccountId() == null
                || command.verifiedPatientProfileId() == null
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.idempotencyKey().trim().length() > 128 || command.correlationId() == null
                || command.correlationId().isBlank() || command.correlationId().length() > 128) {
            throw new AppointmentBookingException("VALIDATION_FAILED");
        }
        String key = command.idempotencyKey().trim();
        String requestFingerprint = fingerprint(FINGERPRINT_PREFIX + command.verifiedPatientProfileId());
        guard.acquire();

        // A successful authorized replay takes precedence over current lifecycle, clinic-day and fee state.
        // A key that this actor used for another appointment is not a replay here; record() rejects it below.
        var replay = idempotency.find(IdempotencyScope.APPOINTMENT_CHECKIN, command.actorAccountId(), key)
                .filter(held -> appointmentId.equals(held.resourceId()));
        if (replay.isPresent()) {
            if (!replay.get().matches(requestFingerprint)) {
                throw new AppointmentBookingException("IDEMPOTENCY_CONFLICT");
            }
            var stored = appointments.findReplay(appointmentId).orElseThrow(
                    () -> new IllegalStateException("Check-in replay row missing for appointment " + appointmentId));
            return new CheckedInAppointment(appointmentId, stored.visitId(), stored.checkedInAt(),
                    reception.findExamQueueTicket(stored.visitId()).orElse(null));
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
        if (!current.patientProfileId().equals(command.verifiedPatientProfileId())) {
            throw new AppointmentBookingException("PATIENT_IDENTITY_MISMATCH");
        }
        requireStartsOnClinicDay(current.startTime());
        requireExamFeePaid(appointmentId);

        ReceptionWorkflowService.CheckInResult receptionResult;
        try {
            receptionResult = reception.checkInAtomically(new ReceptionWorkflowService.CheckInCommand(
                    command.actorAccountId(), appointmentId, current.patientProfileId(), current.doctorProfileId(), key));
        } catch (ReceptionWorkflowException exception) {
            throw new AppointmentBookingException(exception.code(), exception);
        }
        if (receptionResult == null || !appointmentId.equals(receptionResult.appointmentId())
                || !current.patientProfileId().equals(receptionResult.patientProfileId())
                || receptionResult.visitId() == null || receptionResult.checkedInAt() == null) {
            throw new AppointmentBookingException("VISIT_LINK_CONFLICT");
        }
        Instant checkedInAt = receptionResult.checkedInAt();
        appointments.completeCheckIn(current, command.actorAccountId(), receptionResult.visitId(), checkedInAt);
        // Same transaction as the check-in write. False means this actor already holds the key (for another appointment).
        if (!idempotency.record(IdempotencyScope.APPOINTMENT_CHECKIN, command.actorAccountId(), key, requestFingerprint,
                appointmentId)) {
            throw new AppointmentBookingException("IDEMPOTENCY_CONFLICT");
        }
        AppointmentAuditEntries.record(auditLogWriter, AppointmentAuditEntries.changed(current.id(), command.actorAccountId(),
                "RECEPTIONIST", "STATUS_CHANGED", "CONFIRMED", "CHECKED_IN", current.doctorProfileId(),
                current.doctorProfileId(), current.startTime(), current.startTime(), current.endTime(), current.endTime(),
                receptionResult.visitId(), current.sourceVisitId(), null, command.verifiedPatientProfileId(), checkedInAt),
                command.correlationId());
        return new CheckedInAppointment(appointmentId, receptionResult.visitId(), checkedInAt,
                receptionResult.ticket());
    }

    /** Check-in is allowed on the clinic day of the appointment only; there is no minute window. */
    private void requireStartsOnClinicDay(Instant appointmentStart) {
        ZoneId zone = properties.clinicTimeZone();
        if (!appointmentStart.atZone(zone).toLocalDate().equals(Instant.now(clock).atZone(zone).toLocalDate())) {
            throw new AppointmentBookingException("CHECK_IN_TIME_NOT_ALLOWED");
        }
    }

    /** A booked service with a price must have its APPOINTMENT invoice PAID before the visit joins the queue. */
    private void requireExamFeePaid(UUID appointmentId) {
        BigDecimal price;
        try {
            price = billingSources.requireSource(appointmentId).servicePrice();
        } catch (AppointmentQueryException exception) {
            throw new AppointmentBookingException(exception.code(), exception);
        }
        if (price == null || price.signum() <= 0) return;
        if (!invoicePaidStatus.paidStatus(List.of(appointmentId)).getOrDefault(appointmentId, false)) {
            // MSG-94: the exam fee is unpaid, so the patient cannot be received yet (counter and QR check-in alike).
            throw new AppointmentBookingException("MSG-94");
        }
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
