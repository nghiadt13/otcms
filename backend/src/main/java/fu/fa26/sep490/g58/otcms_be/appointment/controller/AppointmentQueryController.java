package fu.fa26.sep490.g58.otcms_be.appointment.controller;

import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentSearchFilter;
import fu.fa26.sep490.g58.otcms_be.billing.service.AppointmentInvoicePaidStatusQueryService;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentSearchPage;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentSummary;
import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentQueryException;
import fu.fa26.sep490.g58.otcms_be.appointment.service.GetAppointmentsService;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response.AppointmentResponse;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response.AppointmentPageResponse;
import fu.fa26.sep490.g58.otcms_be.appointment.mapper.AppointmentMapper;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
public class AppointmentQueryController {
    private static final Set<String> STATUSES = Set.of("CONFIRMED", "CHECKED_IN", "IN_PROGRESS", "COMPLETED", "CANCELLED", "NO_SHOW");
    private final GetAppointmentsService handler;
    private final AppointmentBookingProperties properties;
    private final AppointmentInvoicePaidStatusQueryService billingStatus;
    private final java.time.Clock clock;
    public AppointmentQueryController(GetAppointmentsService handler, AppointmentBookingProperties properties,
            AppointmentInvoicePaidStatusQueryService billingStatus, java.time.Clock clock) {
        this.handler = handler; this.properties = properties; this.billingStatus = billingStatus; this.clock = clock;
    }

    @GetMapping("/api/v1/appointments")
    public ResponseEntity<AppointmentPageResponse> list(@AuthenticationPrincipal AuthenticatedAccount account,
            @RequestParam(required=false) String page, @RequestParam(required=false) String size,
            @RequestParam(required=false) String from, @RequestParam(required=false) String to,
            @RequestParam(required=false) String status, @RequestParam(required=false) String doctorId,
            @RequestParam(required=false) String patientProfileId,
            @RequestParam(required=false) String window, HttpServletRequest request) {
        int p = integer(page, 0), s = integer(size, 20);
        if (p < 0 || s < 1 || s > 100) throw invalid();
        LocalDate f = date(from), t = date(to);
        if (f != null && t != null && f.isAfter(t)) throw invalid();
        Instant lower = f == null ? null : f.atStartOfDay(properties.clinicTimeZone()).toInstant();
        Instant upper;
        try { upper = t == null ? null : t.plusDays(1).atStartOfDay(properties.clinicTimeZone()).toInstant(); }
        catch (DateTimeException ex) { throw invalid(); }
        String normalizedStatus = status == null ? null : status.toUpperCase(java.util.Locale.ROOT);
        if (normalizedStatus != null && !STATUSES.contains(normalizedStatus)) throw invalid();
        String patientWindow = window == null ? null : window.toUpperCase(java.util.Locale.ROOT);
        if (patientWindow != null && (!Set.of("UPCOMING", "PAST").contains(patientWindow)
                || account.accountRole() != AccountRole.PATIENT || normalizedStatus != null
                || doctorId != null)) throw invalid();
        AppointmentSearchPage result = handler.list(account.accountId(), account.accountRole(), new AppointmentSearchFilter(lower, upper, normalizedStatus,
                uuid(doctorId), uuid(patientProfileId), patientWindow, patientWindow == null ? null : clock.instant()), p, s);
        var responses = result.items().stream().map(AppointmentMapper::toResponse).toList();
        var paidByAppointment = billingStatus.paidStatus(responses.stream().map(AppointmentResponse::getId).toList());
        var items = responses.stream().map(item -> item.withExamFeePaid(item.getServicePrice() == null
                ? null : paidByAppointment.getOrDefault(item.getId(), false))).toList();
        return ResponseEntity.ok().header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(new AppointmentPageResponse(items, result.page(), result.size(), result.totalElements(), result.totalPages()));
    }

    @GetMapping("/api/v1/appointments/{id}")
    public ResponseEntity<AppointmentResponse> detail(@PathVariable String id,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        UUID appointmentId = uuid(id);
        AppointmentSummary result = handler.detail(account.accountId(), account.accountRole(), appointmentId);
        AppointmentResponse response = AppointmentMapper.toResponse(result);
        Boolean paid = response.getServicePrice() == null ? null
                : billingStatus.paidStatus(java.util.List.of(response.getId())).getOrDefault(response.getId(), false);
        return ResponseEntity.ok().header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(response.withExamFeePaid(paid));
    }

    private static int integer(String raw, int fallback) {
        if (raw == null) return fallback;
        try { if (!raw.matches("0|[1-9][0-9]*")) throw invalid(); return Integer.parseInt(raw); }
        catch (NumberFormatException ex) { throw invalid(); }
    }
    private static LocalDate date(String raw) {
        if (raw == null) return null;
        try { return LocalDate.parse(raw); } catch (DateTimeException ex) { throw invalid(); }
    }
    private static UUID uuid(String raw) {
        if (raw == null) return null;
        try { UUID id = UUID.fromString(raw); if (!id.toString().equalsIgnoreCase(raw)) throw invalid(); return id; }
        catch (IllegalArgumentException ex) { throw invalid(); }
    }
    private static AppointmentQueryException invalid() { return new AppointmentQueryException("VALIDATION_FAILED"); }
}
