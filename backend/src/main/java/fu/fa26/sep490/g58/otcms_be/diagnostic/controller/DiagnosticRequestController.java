package fu.fa26.sep490.g58.otcms_be.diagnostic.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.diagnostic.mapper.DiagnosticDtoMapper;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticActor;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticRequestView;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.CancellationCause;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.ImagingRequest;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.OperationalRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.OperationalStatusPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.DiagnosticAssignedUploadWorkPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.DiagnosticDtos.AssignedTechnicianUploadWorkPage;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticRequestService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Validated
@PreAuthorize("isAuthenticated()")
@Tag(name = "Diagnostic requests", description = "FEAT-003 requests and assigned image-upload work projections")
public class DiagnosticRequestController {
    private final DiagnosticRequestService requests;
    private final DiagnosticStatusService statuses;
    private final DiagnosticDtoMapper mapper;

    public DiagnosticRequestController(DiagnosticRequestService requests, DiagnosticStatusService statuses,
            DiagnosticDtoMapper mapper) {
        this.requests = requests;
        this.statuses = statuses;
        this.mapper = mapper;
    }

    @GetMapping("/visits/{visitId}/imaging-requests")
    @Operation(operationId = "listVisitImagingRequests", summary = "List operational status for a responsible Visit actor")
    public OperationalStatusPage listByVisit(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedAccount principal,
            @PathVariable UUID visitId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return mapper.toOperationalStatusPage(statuses.listVisitStatus(actor(principal), visitId, page, size));
    }

    @GetMapping("/imaging-requests/assigned-upload-work")
    @Operation(operationId = "listAssignedUploadWork", summary = "List operational upload/send work assigned to this Technician")
    public AssignedTechnicianUploadWorkPage assignedUploadWork(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedAccount principal,
            @RequestParam(required = false) UUID roomId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        DiagnosticAssignedUploadWorkPage work = statuses.listTechnicianUploadWork(
                actor(principal), roomId, page, size);
        return mapper.toAssignedTechnicianUploadWorkPage(work);
    }

    @GetMapping("/imaging-requests/{requestId}")
    @Operation(operationId = "getImagingRequest", summary = "Get request status for a responsible actor")
    public OperationalRequestStatus get(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedAccount principal,
            @PathVariable UUID requestId) {
        return mapper.toOperationalStatus(statuses.getStatus(actor(principal), requestId));
    }

    @PostMapping("/imaging-requests/{requestId}/cancellations")
    @Operation(operationId = "cancelImagingRequest", summary = "Cancel a pre-use request by its treating Doctor")
    public ImagingRequest cancel(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedAccount principal,
            @PathVariable UUID requestId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Expected-Version") @Min(0) long expectedVersion,
            @Valid @RequestBody DiagnosticDtos.CancelRequest body,
            HttpServletRequest request) {
        return mapper.toImagingRequest(requests.cancel(actor(principal), requestId, idempotencyKey,
                expectedVersion, body.cause(), body.reason(), CorrelationIdFilter.current(request)));
    }

    @PostMapping("/imaging-requests/{requestId}/room-transfers")
    @Operation(operationId = "transferImagingRoomBeforeFirstUse", summary = "Transfer a pending request to an eligible room")
    public ImagingRequest transferRoom(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedAccount principal,
            @PathVariable UUID requestId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Expected-Version") @Min(0) long expectedVersion,
            @Valid @RequestBody DiagnosticDtos.RoomTransferRequest body,
            HttpServletRequest request) {
        return mapper.toImagingRequest(requests.transferRoom(actor(principal), requestId, idempotencyKey,
                expectedVersion, body.newRoomId(), body.reason(), CorrelationIdFilter.current(request)));
    }

    private DiagnosticActor actor(AuthenticatedAccount principal) {
        return DiagnosticActor.user(principal.accountId(), principal.accountRole());
    }
}
