package fu.fa26.sep490.g58.otcms_be.visit.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.ExamQueueDtos;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitExamQueueReadService;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VisitExamQueueController {
    private final VisitExamQueueReadService reads;
    private final AppointmentBookingProperties booking;
    private final Clock clock;

    public VisitExamQueueController(VisitExamQueueReadService reads, AppointmentBookingProperties booking,
            Clock clock) {
        this.reads = reads;
        this.booking = booking;
        this.clock = clock;
    }

    @GetMapping("/api/v1/exam-queue")
    @PreAuthorize("hasAnyAuthority('ROLE_RECEPTIONIST','ROLE_ADMIN')")
    public ResponseEntity<ExamQueueDtos.ItemList> queue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request) {
        LocalDate clinicDate = date == null ? LocalDate.now(clock.withZone(booking.clinicTimeZone())) : date;
        return noStore(request).body(reads.listForReception(clinicDate));
    }

    @GetMapping("/api/v1/doctor/exam-queue")
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<ExamQueueDtos.DoctorItemList> doctorQueue(
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        return noStore(request).body(reads.listForDoctorAccount(account));
    }

    @GetMapping("/api/v1/reception/on-shift-doctors")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public ResponseEntity<ExamQueueDtos.OnShiftDoctorList> onShiftDoctors(HttpServletRequest request) {
        return noStore(request).body(reads.onShiftDoctors(clock.instant()));
    }

    @GetMapping("/api/v1/visits/{visitId}/exam-queue-entry")
    @PreAuthorize("hasAnyAuthority('ROLE_RECEPTIONIST','ROLE_DOCTOR','ROLE_PATIENT')")
    public ResponseEntity<ExamQueueDtos.DoctorItem> entry(@PathVariable UUID visitId,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        return noStore(request).body(reads.getEntry(visitId, account));
    }

    private static ResponseEntity.BodyBuilder noStore(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request));
    }
}
