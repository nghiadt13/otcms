package fu.fa26.sep490.g58.otcms_be.imaging.controller;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.dto.response.ImagingOperationResponse;
import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingIngestService;
import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingUploadCommand;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP boundary for assigned technician ingest operations. */
@RestController
@RequestMapping("/api/v1/imaging")
@Validated
public class ImagingIngestController {
    private static final String IDEMPOTENCY_KEY_PATTERN = "[\\x21-\\x7E]{1,128}";

    private final ImagingIngestService ingest;

    public ImagingIngestController(ImagingIngestService ingest) {
        this.ingest = ingest;
    }

    @PostMapping(path = "/requests/{requestId}/attempts/{attemptId}/ingest-operations",
            consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, "application/zip", "application/dicom"})
    @PreAuthorize("hasAuthority('ROLE_IMAGING_TECHNICIAN')")
    public ResponseEntity<ImagingOperationResponse> upload(@PathVariable UUID requestId,
            @PathVariable UUID attemptId,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = IDEMPOTENCY_KEY_PATTERN) String idempotencyKey,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) throws IOException {
        if (account == null || account.accountRole() != AccountRole.IMAGING_TECHNICIAN) {
            throw new ImagingOperationException(ImagingOperationOutcome.UNAUTHORIZED_RECORD);
        }
        try (var body = request.getInputStream()) {
            ImagingOperationResponse response = ImagingOperationResponse.from(ingest.upload(new ImagingUploadCommand(
                    account.accountId(), requestId, attemptId, idempotencyKey, request.getContentType(),
                    request.getContentLengthLong(), body)));
            return ResponseEntity.accepted().location(URI.create(response.statusUrl())).body(response);
        }
    }

    @GetMapping("/ingest-operations/{operationId}")
    @PreAuthorize("hasAuthority('ROLE_IMAGING_TECHNICIAN')")
    public ResponseEntity<ImagingOperationResponse> getOperation(@PathVariable UUID operationId,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        if (account == null || account.accountRole() != AccountRole.IMAGING_TECHNICIAN) {
            throw new ImagingOperationException(ImagingOperationOutcome.UNAUTHORIZED_RECORD);
        }
        return ResponseEntity.ok(ImagingOperationResponse.from(
                ingest.getOperationStatus(account.accountId(), operationId)));
    }

    @PostMapping("/ingest-operations/{operationId}/send")
    @PreAuthorize("hasAuthority('ROLE_IMAGING_TECHNICIAN')")
    public ResponseEntity<ImagingOperationResponse> send(@PathVariable UUID operationId,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = IDEMPOTENCY_KEY_PATTERN) String idempotencyKey,
            @AuthenticationPrincipal AuthenticatedAccount account) {
        if (account == null || account.accountRole() != AccountRole.IMAGING_TECHNICIAN) {
            throw new ImagingOperationException(ImagingOperationOutcome.UNAUTHORIZED_RECORD);
        }
        ImagingOperationResponse response = ImagingOperationResponse.from(
                ingest.send(account.accountId(), operationId, idempotencyKey));
        return ResponseEntity.accepted().location(URI.create(response.statusUrl())).body(response);
    }
}
