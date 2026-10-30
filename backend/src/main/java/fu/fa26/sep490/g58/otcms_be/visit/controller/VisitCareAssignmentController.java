package fu.fa26.sep490.g58.otcms_be.visit.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import fu.fa26.sep490.g58.otcms_be.visit.mapper.CareStepAssignmentMapper;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.request.CareStepAssignmentRequest;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.CareStepAssignmentResponse;
import fu.fa26.sep490.g58.otcms_be.visit.service.CompleteWalkInCareStepCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.ExtendVisitCareAssignmentCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.ManageVisitCareAssignmentService;
import fu.fa26.sep490.g58.otcms_be.visit.service.StartWalkInCareStepCommand;
import fu.fa26.sep490.g58.otcms_be.visit.service.RecordVisitCapacityConflictCommand;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VisitCareAssignmentController {
    private final ManageVisitCareAssignmentService careAssignments;

    public VisitCareAssignmentController(ManageVisitCareAssignmentService careAssignments) {
        this.careAssignments = careAssignments;
    }

    @PostMapping("/api/v1/visits/{visitId}/care-assignments")
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<CareStepAssignmentResponse> startWalkIn(@PathVariable UUID visitId,
            @Valid @RequestBody CareStepAssignmentRequest body,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        var result = careAssignments.startWalkIn(new StartWalkInCareStepCommand(visitId, account.accountId(),
                account.accountRole(), body.getStepCode(), CorrelationIdFilter.current(request)));
        URI location = URI.create("/api/v1/visits/" + visitId + "/care-assignments/" + result.id());
        return ResponseEntity.created(location)
                .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(CareStepAssignmentMapper.toResponse(result));
    }

    @PostMapping("/api/v1/visits/{visitId}/care-assignments/{assignmentId}/extensions")
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<CareStepAssignmentResponse> extend(@PathVariable UUID visitId,
            @PathVariable UUID assignmentId, @Valid @RequestBody CareStepAssignmentRequest body,
            @AuthenticationPrincipal AuthenticatedAccount account,
            HttpServletRequest request) {
        var result = careAssignments.extend(new ExtendVisitCareAssignmentCommand(visitId, assignmentId,
                account.accountId(), account.accountRole(), body.getStepCode(), CorrelationIdFilter.current(request)));
        return ResponseEntity.ok().header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(CareStepAssignmentMapper.toResponse(result));
    }

    @PostMapping("/api/v1/visits/{visitId}/care-assignments/{assignmentId}/capacity-conflicts")
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<Void> recordCapacityConflict(@PathVariable UUID visitId, @PathVariable UUID assignmentId,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        careAssignments.recordCapacityConflict(new RecordVisitCapacityConflictCommand(visitId, assignmentId,
                account.accountId(), account.accountRole(), CorrelationIdFilter.current(request)));
        return ResponseEntity.noContent().header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request)).build();
    }

    @PostMapping("/api/v1/visits/{visitId}/care-assignments/{assignmentId}/completion")
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<CareStepAssignmentResponse> completeWalkIn(@PathVariable UUID visitId,
            @PathVariable UUID assignmentId, @AuthenticationPrincipal AuthenticatedAccount account,
            HttpServletRequest request) {
        var result = careAssignments.completeWalkIn(new CompleteWalkInCareStepCommand(visitId, assignmentId,
                account.accountId(), account.accountRole(), CorrelationIdFilter.current(request)));
        return ResponseEntity.ok().header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .body(CareStepAssignmentMapper.toResponse(result));
    }

}
