package fu.fa26.sep490.g58.otcms_be.visit.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.ReceptionQueueResponse;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitProgressResponse;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitReceptionReadService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VisitReceptionReadController {
    private final VisitReceptionReadService reads;
    private final AppointmentBookingProperties booking;
    private final Clock clock;

    public VisitReceptionReadController(VisitReceptionReadService reads,
            AppointmentBookingProperties booking, Clock clock) {
        this.reads = reads;
        this.booking = booking;
        this.clock = clock;
    }

    @GetMapping("/api/v1/reception-queue")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public ResponseEntity<ReceptionQueueResponse> queue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        if (account == null) throw new VisitOperationException("ACCESS_DENIED");
        LocalDate clinicDate = date == null ? LocalDate.now(clock.withZone(booking.clinicTimeZone())) : date;
        return ResponseEntity.ok().header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(reads.listWalkInQueue(account.accountId(), clinicDate, page, size));
    }

    @GetMapping("/api/v1/visits/{visitId}/progress")
    @PreAuthorize("hasAuthority('ROLE_PATIENT')")
    public ResponseEntity<VisitProgressResponse> progress(@PathVariable UUID visitId,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        if (account == null) throw new VisitOperationException("ACCESS_DENIED");
        return ResponseEntity.ok().header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(reads.getOwnProgress(account.accountId(), visitId));
    }
}
