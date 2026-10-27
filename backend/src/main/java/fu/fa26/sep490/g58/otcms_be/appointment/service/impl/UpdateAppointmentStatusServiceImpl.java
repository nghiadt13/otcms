package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentCapacityConflictException;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentLifecycleRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.UpdateAppointmentStatusCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.UpdateAppointmentStatusService;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.common.exception.CoordinationGuardUnavailableException;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitWorkflowException;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitScheduledCareService;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class UpdateAppointmentStatusServiceImpl implements UpdateAppointmentStatusService {
    private final CoordinationGuardService guard;
    private final AppointmentLifecycleRepository appointments;
    private final DoctorProfileIdentityQueryService doctors;
    private final VisitScheduledCareService visits;
    private final Clock clock;

    public UpdateAppointmentStatusServiceImpl(CoordinationGuardService guard, AppointmentLifecycleRepository appointments,
            DoctorProfileIdentityQueryService doctors, VisitScheduledCareService visits, Clock clock) {
        this.guard = guard; this.appointments = appointments; this.doctors = doctors; this.visits = visits; this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = AppointmentCapacityConflictException.class)
    public CreatedAppointment updateStatus(UUID appointmentId, UpdateAppointmentStatusCommand command) {
        if (appointmentId == null || command == null || command.actorAccountId() == null
                || command.correlationId() == null || command.correlationId().isBlank()
                || command.correlationId().length() > 128 || !"DOCTOR".equals(command.actorType())
                || !java.util.Set.of("IN_PROGRESS", "COMPLETED").contains(command.targetStatus())) {
            throw new AppointmentBookingException("VALIDATION_FAILED");
        }
        try { guard.acquire(); }
        catch (CoordinationGuardUnavailableException failure) {
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", failure);
        } catch (DataAccessException failure) {
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", failure);
        }
        UUID doctorId;
        try {
            doctorId = doctors.findDoctorProfileId(command.actorAccountId())
                    .orElseThrow(() -> new AppointmentBookingException("ACCESS_DENIED"));
        } catch (DataAccessException failure) {
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", failure);
        }
        var current = appointments.findForUpdate(appointmentId)
                .orElseThrow(() -> new AppointmentBookingException("APPOINTMENT_NOT_FOUND"));
        String expected = "IN_PROGRESS".equals(command.targetStatus()) ? "CHECKED_IN" : "IN_PROGRESS";
        if (!expected.equals(current.status())) throw new AppointmentBookingException("APPOINTMENT_NOT_CHANGEABLE");
        if (current.visitId() == null) throw new AppointmentBookingException("VISIT_LINK_CONFLICT");
        if (doctorId == null || !doctorId.equals(current.doctorProfileId())) throw new AppointmentBookingException("ACCESS_DENIED");
        Instant now = Instant.now(clock);
        var ownerCommand = new VisitScheduledCareService.ScheduledCareCommand(appointmentId, current.visitId(),
                current.patientProfileId(), current.doctorProfileId(), current.endTime(), now,
                command.actorAccountId(), current.durationMinutes(), command.correlationId());
        try {
            if ("IN_PROGRESS".equals(command.targetStatus())) visits.startScheduledCare(ownerCommand);
            else if ("COMPLETED".equals(command.targetStatus())) visits.completeScheduledCare(ownerCommand);
            else throw new AppointmentBookingException("VALIDATION_FAILED");
        } catch (VisitWorkflowException failure) {
            String code = failure.code();
            if ("VISIT_CANCELLATION_NOT_ALLOWED".equals(code)) throw new AppointmentBookingException(code, failure);
            if ("VISIT_LINK_CONFLICT".equals(code)) throw new AppointmentBookingException(code, failure);
            if ("VISIT_CAPACITY_NOT_AVAILABLE".equals(code)) {
                throw new AppointmentCapacityConflictException(failure);
            }
            if ("VISIT_CARE_STEP_NOT_CONFIGURED".equals(code)) {
                throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", failure);
            }
            if (code.startsWith("VISIT_CARE_") || "VISIT_CAPACITY_NOT_AVAILABLE".equals(code)) {
                throw new AppointmentBookingException("APPOINTMENT_NOT_CHANGEABLE", failure);
            }
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", failure);
        } catch (DataAccessException failure) {
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", failure);
        }
        try {
            return appointments.transition(current, command.actorAccountId(), command.actorType(),
                    command.targetStatus(), now, command.correlationId());
        } catch (DataAccessException failure) {
            throw new AppointmentBookingException("DEPENDENCY_UNAVAILABLE", failure);
        }
    }
}
