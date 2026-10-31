package fu.fa26.sep490.g58.otcms_be.appointment.controller;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.mapper.AppointmentMapper;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request.CancelAppointmentRequest;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request.CheckInAppointmentRequest;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request.CreateAppointmentRequest;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request.ReassignAppointmentRequest;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request.RescheduleAppointmentRequest;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request.UpdateAppointmentStatusRequest;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response.AppointmentResponse;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response.CheckInResponse;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CancelAppointmentCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CancelAppointmentService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CheckInAppointmentCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CheckInAppointmentService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CreateAppointmentCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CreateAppointmentService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.MarkAppointmentNoShowCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.MarkAppointmentNoShowService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ReassignAppointmentCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ReassignAppointmentService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.RescheduleAppointmentCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.RescheduleAppointmentService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.UpdateAppointmentStatusCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.UpdateAppointmentStatusService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

@RestController
public class AppointmentBookingController {
    private final CreateAppointmentService createAppointment;
    private final RescheduleAppointmentService rescheduleAppointment;
    private final CancelAppointmentService cancelAppointment;
    private final ReassignAppointmentService reassignAppointment;
    private final CheckInAppointmentService checkInAppointment;
    private final MarkAppointmentNoShowService markNoShowAppointment;
    private final UpdateAppointmentStatusService updateAppointmentStatus;

    public AppointmentBookingController(CreateAppointmentService createAppointment,
            RescheduleAppointmentService rescheduleAppointment, CancelAppointmentService cancelAppointment,
            ReassignAppointmentService reassignAppointment, CheckInAppointmentService checkInAppointment,
            MarkAppointmentNoShowService markNoShowAppointment, UpdateAppointmentStatusService updateAppointmentStatus) {
        this.createAppointment = createAppointment;
        this.rescheduleAppointment = rescheduleAppointment;
        this.cancelAppointment = cancelAppointment;
        this.reassignAppointment = reassignAppointment;
        this.checkInAppointment = checkInAppointment;
        this.markNoShowAppointment = markNoShowAppointment;
        this.updateAppointmentStatus = updateAppointmentStatus;
    }

    @PatchMapping("/api/v1/appointments/{id}/status")
    @PreAuthorize("hasAnyAuthority('ROLE_RECEPTIONIST', 'ROLE_DOCTOR')")
    public ResponseEntity<AppointmentResponse> markNoShow(@PathVariable UUID id,
            @Valid @RequestBody UpdateAppointmentStatusRequest body,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        if (body == null || account == null) throw invalid();
        var result = switch (account.accountRole()) {
            case RECEPTIONIST -> {
                if (!"NO_SHOW".equals(body.getTargetStatus())) throw new AppointmentBookingException("ACCESS_DENIED");
                yield markNoShowAppointment.markNoShow(id, new MarkAppointmentNoShowCommand(account.accountId(),
                        body.getReason(), CorrelationIdFilter.current(request)));
            }
            case DOCTOR -> {
                if (body.hasReasonProperty() || !Set.of("IN_PROGRESS", "COMPLETED").contains(body.getTargetStatus())) {
                    throw new AppointmentBookingException("ACCESS_DENIED");
                }
                yield updateAppointmentStatus.updateStatus(id, new UpdateAppointmentStatusCommand(account.accountId(),
                        "DOCTOR", body.getTargetStatus(), CorrelationIdFilter.current(request)));
            }
            default -> throw new AppointmentBookingException("ACCESS_DENIED");
        };
        return ResponseEntity.ok().header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(AppointmentMapper.toResponse(result));
    }

    @PostMapping("/api/v1/appointments/{id}/check-in")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public ResponseEntity<CheckInResponse> checkIn(@PathVariable UUID id,
            @Valid @RequestBody CheckInAppointmentRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        if (body == null || account == null || idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.trim().length() > 128) throw invalid();
        var result = checkInAppointment.checkIn(id, new CheckInAppointmentCommand(account.accountId(),
                body.getVerifiedPatientProfileId(), idempotencyKey.trim(), CorrelationIdFilter.current(request)));
        return ResponseEntity.ok().header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(AppointmentMapper.toResponse(result));
    }

    @PostMapping("/api/v1/appointments/{id}/reassign")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public ResponseEntity<AppointmentResponse> reassign(@PathVariable UUID id,
            @Valid @RequestBody ReassignAppointmentRequest body,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        if (body == null || account == null) throw invalid();
        var result = reassignAppointment.reassign(id, new ReassignAppointmentCommand(account.accountId(),
                body.getDoctorId(), body.getReason(), CorrelationIdFilter.current(request)));
        return ResponseEntity.ok().header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(AppointmentMapper.toResponse(result));
    }

    @PostMapping("/api/v1/appointments/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ROLE_PATIENT', 'ROLE_RECEPTIONIST')")
    public ResponseEntity<AppointmentResponse> cancel(@PathVariable UUID id,
            @Valid @RequestBody CancelAppointmentRequest body,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        if (body == null || account == null) throw invalid();
        CancelAppointmentCommand.Actor actor = switch (account.accountRole()) {
            case PATIENT -> CancelAppointmentCommand.Actor.PATIENT;
            case RECEPTIONIST -> CancelAppointmentCommand.Actor.RECEPTIONIST;
            default -> throw new AppointmentBookingException("ACCESS_DENIED");
        };
        var result = cancelAppointment.cancel(id, new CancelAppointmentCommand(account.accountId(), actor,
                body.getReason(), CorrelationIdFilter.current(request)));
        return ResponseEntity.ok().header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(AppointmentMapper.toResponse(result));
    }

    @PatchMapping("/api/v1/appointments/{id}/reschedule")
    @PreAuthorize("hasAnyAuthority('ROLE_PATIENT', 'ROLE_RECEPTIONIST')")
    public ResponseEntity<AppointmentResponse> reschedule(@PathVariable UUID id,
            @Valid @RequestBody RescheduleAppointmentRequest body,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        if (body == null || account == null) throw invalid();
        RescheduleAppointmentCommand.Actor actor = switch (account.accountRole()) {
            case PATIENT -> RescheduleAppointmentCommand.Actor.PATIENT;
            case RECEPTIONIST -> RescheduleAppointmentCommand.Actor.RECEPTIONIST;
            default -> throw new AppointmentBookingException("ACCESS_DENIED");
        };
        var result = rescheduleAppointment.reschedule(id, new RescheduleAppointmentCommand(account.accountId(), actor,
                body.getStartTime(), body.getReason(), CorrelationIdFilter.current(request)));
        return ResponseEntity.ok().header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(AppointmentMapper.toResponse(result));
    }

    @PostMapping("/api/v1/appointments")
    @PreAuthorize("hasAnyAuthority('ROLE_PATIENT', 'ROLE_RECEPTIONIST')")
    public ResponseEntity<AppointmentResponse> create(@Valid @RequestBody CreateAppointmentRequest body,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        if (body == null || account == null) throw invalid();
        CreateAppointmentCommand.Actor actor = switch (account.accountRole()) {
            case PATIENT -> CreateAppointmentCommand.Actor.PATIENT;
            case RECEPTIONIST -> CreateAppointmentCommand.Actor.RECEPTIONIST;
            default -> throw new AppointmentBookingException("ACCESS_DENIED");
        };
        CreatedAppointment created = createAppointment.create(new CreateAppointmentCommand(account.accountId(), actor,
                body.getPatientProfileId(), body.getServiceId(), body.getDoctorId(), body.getStartTime(),
                body.getPurpose(), CorrelationIdFilter.current(request)));
        URI location = URI.create("/api/v1/appointments/" + created.id());
        return ResponseEntity.created(location).header(CorrelationIdFilter.HEADER,
                        CorrelationIdFilter.current(request)).body(AppointmentMapper.toResponse(created));
    }

    private AppointmentBookingException invalid() {
        return new AppointmentBookingException("VALIDATION_FAILED");
    }
}
