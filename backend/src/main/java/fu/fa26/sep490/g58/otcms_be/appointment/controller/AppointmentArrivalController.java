package fu.fa26.sep490.g58.otcms_be.appointment.controller;

import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentArrivalApplicationService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ClinicArrivalProofService;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request.RecordManualArrivalRequest;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request.RecordQrArrivalRequest;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response.ArrivalResponse;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response.ArrivalWorklistResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppointmentArrivalController {
    private final AppointmentArrivalApplicationService arrivals;
    private final ClinicArrivalProofService proofs;
    private final AppointmentBookingProperties booking;
    private final Clock clock;

    public AppointmentArrivalController(AppointmentArrivalApplicationService arrivals,
            ClinicArrivalProofService proofs, AppointmentBookingProperties booking, Clock clock) {
        this.arrivals = arrivals;
        this.proofs = proofs;
        this.booking = booking;
        this.clock = clock;
    }

    @GetMapping("/api/v1/arrival-proofs/current")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public ResponseEntity<ClinicArrivalProofService.ArrivalProof> currentProof() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(proofs.issue());
    }

    @PostMapping("/api/v1/appointments/{appointmentId}/arrivals")
    @PreAuthorize("hasAuthority('ROLE_PATIENT')")
    public ResponseEntity<ArrivalResponse> recordQr(@PathVariable UUID appointmentId,
            @Valid @RequestBody RecordQrArrivalRequest body,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        if (account == null) throw new AppointmentBookingException("ACCESS_DENIED");
        return ResponseEntity.ok().header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(arrivals.recordQr(appointmentId, account.accountId(), body.proof()));
    }

    @PostMapping("/api/v1/appointments/{appointmentId}/arrivals/manual")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public ResponseEntity<ArrivalResponse> recordManual(@PathVariable UUID appointmentId,
            @Valid @RequestBody RecordManualArrivalRequest body,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        if (account == null) throw new AppointmentBookingException("ACCESS_DENIED");
        return ResponseEntity.ok().header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(arrivals.recordManual(appointmentId, account.accountId(), body.reason()));
    }

    @GetMapping("/api/v1/appointment-arrivals")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public ResponseEntity<ArrivalWorklistResponse> worklist(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String state,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest request) {
        LocalDate clinicDate = date == null ? LocalDate.now(clock.withZone(booking.clinicTimeZone())) : date;
        return ResponseEntity.ok().header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(arrivals.list(clinicDate, state, page, size));
    }
}
