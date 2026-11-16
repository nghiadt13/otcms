package fu.fa26.sep490.g58.otcms_be.clinical.controller;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.MedicalDocumentDtos.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalMedicalDocumentService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalMedicalDocumentService.*;
import fu.fa26.sep490.g58.otcms_be.clinical.validation.ClinicalMedicalDocumentFileValidator;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ClinicalMedicalDocumentController {
    private final ClinicalMedicalDocumentService documents;
    public ClinicalMedicalDocumentController(ClinicalMedicalDocumentService documents) { this.documents = documents; }

    @PostMapping(value = "/api/v1/visits/{visitId}/medical-documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_DOCTOR','ROLE_RECEPTIONIST')")
    public ResponseEntity<DocumentView> upload(@PathVariable UUID visitId, @RequestPart("metadata") @Valid UploadMetadata metadata,
            @RequestPart("file") MultipartFile file, @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        return response(201, documents.upload(account, visitId, metadata.documentType(), metadata.title(), file(file)), request);
    }
    @PutMapping(value = "/api/v1/visits/{visitId}/medical-documents/{documentId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<DocumentView> update(@PathVariable UUID visitId, @PathVariable UUID documentId,
            @RequestPart("metadata") @Valid UpdateMetadata metadata, @RequestPart(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        return response(200, documents.update(account, visitId, documentId, metadata.version(), metadata.title(), file(file)), request);
    }
    @PostMapping("/api/v1/visits/{visitId}/medical-documents/{documentId}/release")
    @PreAuthorize("hasAuthority('ROLE_DOCTOR')")
    public ResponseEntity<DocumentView> release(@PathVariable UUID visitId, @PathVariable UUID documentId,
            @RequestBody @Valid ReleaseRequest body, @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        return response(200, documents.release(account, visitId, documentId, body.version()), request);
    }
    @GetMapping("/api/v1/visits/{visitId}/medical-documents")
    @PreAuthorize("hasAnyAuthority('ROLE_DOCTOR','ROLE_RECEPTIONIST','ROLE_PATIENT')")
    public ResponseEntity<Map<String, List<DocumentView>>> list(@PathVariable UUID visitId,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        return response(200, Map.of("items", documents.list(account, visitId)), request);
    }
    @GetMapping("/api/v1/medical-documents/{documentId}")
    @PreAuthorize("hasAnyAuthority('ROLE_DOCTOR','ROLE_RECEPTIONIST','ROLE_PATIENT')")
    public ResponseEntity<DocumentView> get(@PathVariable UUID documentId, @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        return response(200, documents.get(account, documentId), request);
    }
    @GetMapping("/api/v1/medical-documents/{documentId}/content")
    @PreAuthorize("hasAnyAuthority('ROLE_DOCTOR','ROLE_RECEPTIONIST','ROLE_PATIENT')")
    public ResponseEntity<byte[]> content(@PathVariable UUID documentId, @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        var result = documents.content(account, documentId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request)).header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(result.document().originalFilename(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(result.document().mediaType())).contentLength(result.bytes().length).body(result.bytes());
    }
    @GetMapping("/api/v1/medical-documents/{documentId}/view")
    @PreAuthorize("hasAuthority('ROLE_PATIENT')")
    public ResponseEntity<byte[]> viewReleasedPdf(@PathVariable UUID documentId,
            @AuthenticationPrincipal AuthenticatedAccount account, HttpServletRequest request) {
        var result = documents.content(account, documentId);
        if (!MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(result.document().mediaType())) {
            throw new ClinicalOperationException("MSG-41");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(result.document().originalFilename(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_PDF).contentLength(result.bytes().length).body(result.bytes());
    }
    private static DocumentFile file(MultipartFile file) {
        if (file == null) return null;
        if (file.isEmpty() || file.getSize() > ClinicalMedicalDocumentFileValidator.MAX_BYTES) throw new ClinicalOperationException("MSG-47");
        try { return new DocumentFile(file.getOriginalFilename(), file.getContentType(), file.getBytes()); }
        catch (IOException failure) { throw new ClinicalOperationException("DEPENDENCY_UNAVAILABLE"); }
    }
    private static <T> ResponseEntity<T> response(int status, T body, HttpServletRequest request) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .header(CorrelationIdFilter.HEADER, CorrelationIdFilter.current(request)).body(body);
    }
}
