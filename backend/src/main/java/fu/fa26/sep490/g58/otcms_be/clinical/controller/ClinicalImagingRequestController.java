package fu.fa26.sep490.g58.otcms_be.clinical.controller;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ImagingRequestDtos.*;
import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalDoctorIdentityResolver;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalImagingRequestService;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ClinicalImagingRequestController {
    private final ClinicalImagingRequestService imagingRequests;
    private final ClinicalDoctorIdentityResolver doctors;

    public ClinicalImagingRequestController(ClinicalImagingRequestService imagingRequests, ClinicalDoctorIdentityResolver doctors) {
        this.imagingRequests = imagingRequests;
        this.doctors = doctors;
    }

    @PostMapping("/api/v1/visits/{visitId}/imaging-requests")
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<ImagingRoundResponse> submit(@PathVariable UUID visitId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody ImagingRequest body,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        if (key.isBlank() || key.length() > 128) throw new ClinicalOperationException("MSG-39");
        var command = new ClinicalImagingRequestService.ImagingRequestCommand(visitId, doctors.requireDoctorProfile(account),
                key, body.orders().stream().map(order -> new ClinicalImagingRequestService.ImagingOrderRequest(
                        order.serviceId(), order.clinicalReason(), order.bodyArea(), order.instructions())).toList(), Boolean.TRUE.equals(body.patientConsentConfirmed()));
        var result = imagingRequests.submit(command);
        var round = result.round();
        var response = new ImagingRoundResponse(round.id(), round.visitId(), round.roundNumber(), round.orderedAt(),
                round.orders().stream().map(order -> new ImagingOrderResponse(order.orderId(), order.orderCode(),
                        order.serviceId(), order.serviceName(), order.serviceType(), order.clinicalReason(),
                        order.bodyArea(), order.instructions(), order.priceSnapshot(), order.orderStatus(), order.paymentStatus())).toList());
        return ResponseEntity.status(result.replayed() ? 200 : 201).cacheControl(CacheControl.noStore())
                .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request)).body(response);
    }
}
