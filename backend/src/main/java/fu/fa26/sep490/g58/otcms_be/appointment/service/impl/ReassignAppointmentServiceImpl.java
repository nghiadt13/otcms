package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.repository.AppointmentReassignmentRepository;
import fu.fa26.sep490.g58.otcms_be.appointment.service.GenerateAvailableSlotsService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ReassignAppointmentCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ReassignAppointmentService;

import fu.fa26.sep490.g58.otcms_be.appointment.model.SlotAvailabilityRequest;
import fu.fa26.sep490.g58.otcms_be.appointment.model.SlotServiceContext;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.ServiceDoctorAssignmentQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Service
public class ReassignAppointmentServiceImpl implements ReassignAppointmentService {
    private final CoordinationGuardService guard;
    private final AppointmentReassignmentRepository appointments;
    private final ServiceDoctorAssignmentQueryService assignments;
    private final StaffAvailabilityProviderQueryService providers;
    private final GenerateAvailableSlotsService slotEngine;
    private final AppointmentBookingProperties properties;
    private final Clock clock;

    public ReassignAppointmentServiceImpl(CoordinationGuardService guard, AppointmentReassignmentRepository appointments,
            ServiceDoctorAssignmentQueryService assignments, StaffAvailabilityProviderQueryService providers,
            GenerateAvailableSlotsService slotEngine, AppointmentBookingProperties properties, Clock clock) {
        this.guard = guard;
        this.appointments = appointments;
        this.assignments = assignments;
        this.providers = providers;
        this.slotEngine = slotEngine;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CreatedAppointment reassign(UUID appointmentId, ReassignAppointmentCommand command) {
        if (appointmentId == null || command == null || command.actorAccountId() == null
                || command.newDoctorProfileId() == null || command.reason() == null || command.reason().isBlank()
                || command.reason().trim().length() > 1000 || command.correlationId() == null
                || command.correlationId().isBlank() || command.correlationId().length() > 128) {
            throw new AppointmentBookingException("VALIDATION_FAILED");
        }
        guard.acquire();
        var current = appointments.findForUpdate(appointmentId)
                .orElseThrow(() -> new AppointmentBookingException("APPOINTMENT_NOT_FOUND"));
        Instant now = Instant.now(clock);
        if (!"CONFIRMED".equals(current.status())) {
            throw new AppointmentBookingException("APPOINTMENT_NOT_CHANGEABLE");
        }
        if (current.startTime().isBefore(now)) throw new AppointmentBookingException("BOOKING_TIME_IN_PAST");
        UUID replacement = command.newDoctorProfileId();
        if (replacement.equals(current.doctorProfileId())) throw new AppointmentBookingException("VALIDATION_FAILED");
        boolean acceptingInDirectory = providers.findAcceptingDoctorsInActiveDirectory().stream()
                .anyMatch(provider -> provider.doctorProfileId().equals(replacement));
        boolean assigned = assignments.findActiveDoctorProfileIds(current.serviceId()).contains(replacement);
        if (!acceptingInDirectory || !assigned) throw new AppointmentBookingException("DOCTOR_NOT_ELIGIBLE");
        LocalDate clinicDate = LocalDate.ofInstant(current.startTime(), properties.clinicTimeZone());
        var slotRequest = new SlotAvailabilityRequest(Set.of(replacement), clinicDate, clinicDate,
                SlotServiceContext.snapshot(current.serviceId(), current.durationMinutes(), current.bufferMinutes(),
                        current.id(), current.patientProfileId()));
        boolean slotAvailable = slotEngine.generate(slotRequest).stream()
                .anyMatch(slot -> slot.doctorProfileId().equals(replacement) && slot.startTime().equals(current.startTime())
                        && slot.endTime().equals(current.endTime()));
        if (!slotAvailable) throw new AppointmentBookingException("SLOT_UNAVAILABLE");
        return appointments.reassign(current, replacement, command.actorAccountId(), command.reason().trim(), now,
                command.correlationId());
    }
}
