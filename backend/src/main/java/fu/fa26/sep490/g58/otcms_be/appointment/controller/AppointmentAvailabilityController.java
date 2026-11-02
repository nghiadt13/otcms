package fu.fa26.sep490.g58.otcms_be.appointment.controller;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentAvailabilityException;

import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentAvailabilityQuery;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentAvailabilitySlot;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response.AppointmentAvailabilityResponse;
import fu.fa26.sep490.g58.otcms_be.appointment.service.GetAppointmentAvailabilityService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.GetAppointmentChangeAvailabilityService;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@RestController
public class AppointmentAvailabilityController {
    private final GetAppointmentAvailabilityService availability;
    private final GetAppointmentChangeAvailabilityService changes;

    public AppointmentAvailabilityController(GetAppointmentAvailabilityService availability,
                                             GetAppointmentChangeAvailabilityService changes) {
        this.availability = availability;
        this.changes = changes;
    }

    @GetMapping("/api/v1/appointments/availability")
    @Operation(security = {})
    @ResponseStatus(HttpStatus.OK)
    public AppointmentAvailabilityResponse publicAvailability(@RequestParam(required = false) String serviceId,
                                        @RequestParam(required = false) String from,
                                        @RequestParam(required = false) String to,
                                        @RequestParam(required = false) String doctorId) {
        return response(availability.find(query(serviceId, from, to, doctorId,
                AppointmentAvailabilityQuery.Audience.PUBLIC)));
    }

    @GetMapping("/api/v1/staff/appointments/availability")
    @PreAuthorize("hasAnyAuthority('ROLE_RECEPTIONIST', 'ROLE_DOCTOR', 'ROLE_ADMIN')")
    @ResponseStatus(HttpStatus.OK)
    public AppointmentAvailabilityResponse staffAvailability(@RequestParam(required = false) String serviceId,
                                       @RequestParam(required = false) String from,
                                       @RequestParam(required = false) String to,
                                       @RequestParam(required = false) String doctorId) {
        return response(availability.find(query(serviceId, from, to, doctorId,
                AppointmentAvailabilityQuery.Audience.STAFF)));
    }

    @GetMapping("/api/v1/appointments/{appointmentId}/reschedule-availability")
    @PreAuthorize("hasAnyAuthority('ROLE_PATIENT', 'ROLE_RECEPTIONIST')")
    @ResponseStatus(HttpStatus.OK)
    public AppointmentAvailabilityResponse rescheduleAvailability(@org.springframework.web.bind.annotation.PathVariable String appointmentId,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        var actor = SecurityContextHolder.getContext().getAuthentication();
        boolean patient = actor.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_PATIENT"));
        UUID accountId = actor.getPrincipal() instanceof AuthenticatedAccount account ? account.accountId() : null;
        return response(changes.findRescheduleSlots(parseUuid(appointmentId), accountId,
                patient ? AppointmentAvailabilityQuery.Audience.PUBLIC : AppointmentAvailabilityQuery.Audience.STAFF,
                parseDate(from), parseDate(to)));
    }

    @GetMapping("/api/v1/appointments/{appointmentId}/reassignment-availability")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    @ResponseStatus(HttpStatus.OK)
    public AppointmentAvailabilityResponse reassignmentAvailability(@org.springframework.web.bind.annotation.PathVariable String appointmentId,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return response(changes.findReassignmentSlots(parseUuid(appointmentId), parseDate(from), parseDate(to)));
    }

    private AppointmentAvailabilityResponse response(List<AppointmentAvailabilitySlot> slots) {
        return new AppointmentAvailabilityResponse(slots.stream().map(slot -> new AppointmentAvailabilityResponse.Slot(slot.serviceId(), slot.doctorId(),
                slot.doctorDisplayName(), slot.startTime(), slot.endTime(), slot.occupiedUntil())).toList());
    }

    private AppointmentAvailabilityQuery query(String serviceId, String from, String to, String doctorId,
                                               AppointmentAvailabilityQuery.Audience audience) {
        return new AppointmentAvailabilityQuery(parseUuid(serviceId), parseDate(from), parseDate(to),
                doctorId == null ? null : parseUuid(doctorId), audience);
    }

    private UUID parseUuid(String value) {
        if (value == null) return null;
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentAvailabilityException(
                    "VALIDATION_FAILED");
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentAvailabilityException(
                    "VALIDATION_FAILED");
        }
    }

}
