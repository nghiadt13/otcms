package fu.fa26.sep490.g58.otcms_be.appointment.controller;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentQueryException;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response.AdminAppointmentPageResponse;
import fu.fa26.sep490.g58.otcms_be.appointment.model.dto.response.AdminAppointmentResponse;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentSearchFilter;
import fu.fa26.sep490.g58.otcms_be.appointment.service.GetAdminAppointmentsService;
import fu.fa26.sep490.g58.otcms_be.billing.service.AppointmentInvoicePaidStatusQueryService;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminAppointmentQueryController {
    private static final Set<String> STATUSES = Set.of(
            "CONFIRMED", "CHECKED_IN", "IN_PROGRESS", "COMPLETED", "CANCELLED", "NO_SHOW");
    private final GetAdminAppointmentsService appointments;
    private final AppointmentBookingProperties properties;
    private final AppointmentInvoicePaidStatusQueryService billingStatus;

    public AdminAppointmentQueryController(GetAdminAppointmentsService appointments,
            AppointmentBookingProperties properties, AppointmentInvoicePaidStatusQueryService billingStatus) {
        this.appointments = appointments;
        this.properties = properties;
        this.billingStatus = billingStatus;
    }

    @GetMapping("/api/v1/admin/appointments")
    public ResponseEntity<AdminAppointmentPageResponse> list(
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String doctorId,
            HttpServletRequest request) {
        int p = integer(page, 0), s = integer(size, 20);
        if (p < 0 || s < 1 || s > 100) throw invalid();
        LocalDate f = date(from), t = date(to);
        if (f != null && t != null && f.isAfter(t)) throw invalid();
        Instant lower = f == null ? null : f.atStartOfDay(properties.clinicTimeZone()).toInstant();
        Instant upper;
        try {
            upper = t == null ? null : t.plusDays(1).atStartOfDay(properties.clinicTimeZone()).toInstant();
        } catch (DateTimeException exception) {
            throw invalid();
        }
        String normalizedStatus = status == null ? null : status.toUpperCase(java.util.Locale.ROOT);
        if (normalizedStatus != null && !STATUSES.contains(normalizedStatus)) throw invalid();
        var result = appointments.list(new AppointmentSearchFilter(lower, upper, normalizedStatus,
                uuid(doctorId), null), p, s);
        var projected = result.items().stream().map(AdminAppointmentResponse::from).toList();
        var paidByAppointment = billingStatus.paidStatus(projected.stream()
                .map(AdminAppointmentResponse::id).toList());
        var response = new AdminAppointmentPageResponse(projected.stream()
                .map(item -> item.withExamFeePaid(item.servicePrice() == null ? null
                        : paidByAppointment.getOrDefault(item.id(), false))).toList(), result.page(), result.size(),
                result.totalElements(), result.totalPages());
        return ResponseEntity.ok().header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(response);
    }

    private static int integer(String raw, int fallback) {
        if (raw == null) return fallback;
        try {
            if (!raw.matches("0|[1-9][0-9]*")) throw invalid();
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            throw invalid();
        }
    }

    private static LocalDate date(String raw) {
        if (raw == null) return null;
        try { return LocalDate.parse(raw); }
        catch (DateTimeException exception) { throw invalid(); }
    }

    private static UUID uuid(String raw) {
        if (raw == null) return null;
        try {
            UUID id = UUID.fromString(raw);
            if (!id.toString().equalsIgnoreCase(raw)) throw invalid();
            return id;
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
    }

    private static AppointmentQueryException invalid() {
        return new AppointmentQueryException("VALIDATION_FAILED");
    }
}
