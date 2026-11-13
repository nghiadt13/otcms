package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.mapper.AppointmentAuditEntries;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;

import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentPaymentHoldRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ConfirmAppointmentPaymentService;
import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationMessages;
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * AWAITING_PAYMENT -> CONFIRMED once billing records a completed payment. Runs inside the payment transaction (the
 * invoice is already locked, so this takes the appointment row lock second) and never throws for an expected state.
 */
@Service
public class ConfirmAppointmentPaymentServiceImpl implements ConfirmAppointmentPaymentService {
    private final AuditLogWriter auditLogWriter;
    /** Statuses reached only through CONFIRMED: the booking was already confirmed earlier. */
    private static final Set<String> CONFIRMED_OR_LATER =
            Set.of("CONFIRMED", "CHECKED_IN", "IN_PROGRESS", "COMPLETED", "NO_SHOW");
    private static final String CONFIRM_REASON = "Đã thanh toán";

    private final AppointmentPaymentHoldRepository holds;
    private final NotificationPublisher notifications;
    private final StaffAvailabilityProviderQueryService doctors;
    private final Clock clock;

    public ConfirmAppointmentPaymentServiceImpl(AppointmentPaymentHoldRepository holds,
            NotificationPublisher notifications, StaffAvailabilityProviderQueryService doctors, Clock clock, AuditLogWriter auditLogWriter) {
        this.auditLogWriter = auditLogWriter;
        this.holds = holds;
        this.notifications = notifications;
        this.doctors = doctors;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Result confirmPaid(UUID appointmentId) {
        if (appointmentId == null) return Result.NOT_AWAITING;
        var row = holds.findForUpdate(appointmentId).orElse(null);
        if (row == null) return Result.NOT_AWAITING;
        if (CONFIRMED_OR_LATER.contains(row.status())) return Result.ALREADY_CONFIRMED;
        if (!"AWAITING_PAYMENT".equals(row.status())) return Result.NOT_AWAITING;
        Instant now = Instant.now(clock);
        if (holds.markConfirmed(appointmentId, now) != 1) return Result.ALREADY_CONFIRMED;
        // Payment is confirmed by the system on behalf of the account that booked.
        AppointmentAuditEntries.record(auditLogWriter, AppointmentAuditEntries.changed(appointmentId, row.createdByAccountId(),
                "SYSTEM", "STATUS_CHANGED", "AWAITING_PAYMENT", "CONFIRMED", row.doctorProfileId(),
                row.doctorProfileId(), row.startTime(), row.startTime(), row.endTime(), row.endTime(),
                null, null, CONFIRM_REASON, null, now), "payment-confirmed-" + appointmentId);
        // The booking notification was deferred while the slot was only held; it goes out exactly once, here.
        notifications.publishToPatient(row.patientProfileId(), NotificationMessages.appointmentBooked(appointmentId,
                row.startTime(), doctors.findDoctorDisplayName(row.doctorProfileId()).orElse("")));
        return Result.CONFIRMED;
    }
}
