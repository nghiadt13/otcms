package fu.fa26.sep490.g58.otcms_be.visit.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.mapper.WalkInVisitMapper;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.request.CreateWalkInVisitRequest;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.WalkInVisitResponse;
import fu.fa26.sep490.g58.otcms_be.visit.service.CreateWalkInVisitCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.CreateWalkInVisitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WalkInVisitController {
    private final CreateWalkInVisitService createWalkInVisit;

    public WalkInVisitController(CreateWalkInVisitService createWalkInVisit) {
        this.createWalkInVisit = createWalkInVisit;
    }

    @PostMapping("/api/v1/visits/walk-ins")
    @PreAuthorize("hasAuthority('ROLE_RECEPTIONIST')")
    public ResponseEntity<WalkInVisitResponse> create(@Valid @RequestBody CreateWalkInVisitRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        if (account == null) throw new VisitOperationException("ACCESS_DENIED");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.trim().length() > 128) {
            throw invalid();
        }
        var created = createWalkInVisit.create(new CreateWalkInVisitCommand(account.accountId(), account.accountRole(),
                body.getPatientProfileId(), body.getPriorAppointmentId(), idempotencyKey.trim(),
                CorrelationIdFilter.current(request)));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(WalkInVisitMapper.toResponse(created));
    }

    private static VisitOperationException invalid() {
        return new VisitOperationException("VALIDATION_FAILED");
    }
}
