package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.model.NoShowAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentNoShowRepository;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorWorkShift;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorScheduleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.UUID;

/**
 * One automatic no-show decision (BR-64) in its own transaction, so a failing booking never rolls back the others.
 */
@Service
public class MarkNoShowAfterShiftTransaction {
    /** Stored as the audit reason of an automatic no-show. */
    static final String SHIFT_ENDED_REASON = "Hết ca làm việc của bác sĩ, bệnh nhân không đến";

    enum Outcome {
        /** Now NO_SHOW. */
        MARKED,
        /** Still CONFIRMED and due later (the shift is running), or the attempt failed: the next sweep decides again. */
        PENDING,
        /** No longer a CONFIRMED, unchecked-in booking (changed since it was selected). */
        NOT_CANDIDATE
    }

    private final CoordinationGuardService guard;
    private final AppointmentNoShowRepository appointments;
    private final DoctorScheduleService schedules;
    private final AppointmentBookingProperties properties;

    public MarkNoShowAfterShiftTransaction(CoordinationGuardService guard, AppointmentNoShowRepository appointments,
            DoctorScheduleService schedules, AppointmentBookingProperties properties) {
        this.guard = guard;
        this.appointments = appointments;
        this.schedules = schedules;
        this.properties = properties;
    }

    /** BR-64: a booking that was not checked in becomes NO_SHOW once the doctor's shift covering it has ended. */
    @Transactional
    public Outcome markNoShow(UUID appointmentId, Instant now) {
        guard.acquire();
        var found = appointments.findForUpdate(appointmentId)
                .filter(row -> "CONFIRMED".equals(row.status()) && row.visitId() == null);
        if (found.isEmpty()) return Outcome.NOT_CANDIDATE;
        NoShowAppointment current = found.get();
        if (!now.isAfter(shiftEnd(current))) return Outcome.PENDING;
        // The system acts on behalf of the account that created the booking (audit actor_account_id is NOT NULL).
        UUID creator = appointments.createdByAccountId(appointmentId);
        appointments.markNoShow(current, creator, "SYSTEM", SHIFT_ENDED_REASON, now,
                "no-show-sweep-" + appointmentId);
        return Outcome.MARKED;
    }

    /**
     * End of the doctor's shift that covers the booking start. When no shift covers it, the end of the booking's clinic
     * day (clinic time zone).
     */
    private Instant shiftEnd(NoShowAppointment appointment) {
        var zone = properties.clinicTimeZone();
        LocalDate day = LocalDate.ofInstant(appointment.startTime(), zone);
        Instant dayStart = day.atStartOfDay(zone).toInstant();
        Instant dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant();
        Instant start = appointment.startTime();
        return schedules.forProfile(appointment.doctorProfileId(), dayStart, dayEnd).stream()
                .filter(shift -> !shift.startsAt().isAfter(start) && shift.endsAt().isAfter(start))
                .map(DoctorWorkShift::endsAt)
                .max(Comparator.naturalOrder())
                .orElse(dayEnd);
    }
}
