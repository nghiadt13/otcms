package fu.fa26.sep490.g58.otcms_be.appointment.controller;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.mapper.AppointmentMapper;
import fu.fa26.sep490.g58.otcms_be.appointment.model.CreatedAppointment;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request.CreateFollowUpAppointmentRequest;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response.AppointmentResponse;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CreateFollowUpAppointmentCommand;
import fu.fa26.sep490.g58.otcms_be.appointment.service.CreateFollowUpAppointmentService;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
public class AppointmentFollowUpController {
    private final CreateFollowUpAppointmentService createFollowUp;

    public AppointmentFollowUpController(CreateFollowUpAppointmentService createFollowUp) {
        this.createFollowUp = createFollowUp;
    }

    @PostMapping("/api/v1/visits/{visitId}/follow-up-appointments")
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<AppointmentResponse> create(@PathVariable UUID visitId,
            @Valid @RequestBody CreateFollowUpAppointmentRequest body,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        if (account == null) throw new AppointmentBookingException("ACCESS_DENIED");
        CreatedAppointment created = createFollowUp.create(new CreateFollowUpAppointmentCommand(account.accountId(),
                visitId, body.getServiceId(), body.getDoctorId(), body.getStartTime(), body.getPurpose(), body.getReason(),
                CorrelationIdFilter.current(request)));
        return ResponseEntity.created(URI.create("/api/v1/appointments/" + created.id()))
                .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(AppointmentMapper.toResponse(created));
    }

}
