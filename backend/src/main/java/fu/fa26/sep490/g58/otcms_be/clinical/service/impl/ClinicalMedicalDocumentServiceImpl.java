package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.*;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ClinicalMedicalDocumentJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.*;
import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalMedicalDocumentAccess;
import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalMedicalDocumentAccess.Scope;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalMedicalDocumentService;
import fu.fa26.sep490.g58.otcms_be.clinical.validation.ClinicalMedicalDocumentFileValidator;
import fu.fa26.sep490.g58.otcms_be.common.security.*;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ClinicalMedicalDocumentServiceImpl implements ClinicalMedicalDocumentService {
    private final ClinicalMedicalDocumentAccess access;
    private final ClinicalMedicalDocumentRepository documents;
    private final ClinicalMedicalDocumentPayloadRepository payloads;
    private final ClinicalExaminationRepository examinations;
    private final ClinicalMedicalDocumentFileValidator files;
    private final Clock clock;
    public ClinicalMedicalDocumentServiceImpl(ClinicalMedicalDocumentAccess access, ClinicalMedicalDocumentRepository documents,
            ClinicalMedicalDocumentPayloadRepository payloads, ClinicalExaminationRepository examinations,
            ClinicalMedicalDocumentFileValidator files, Clock clock) {
        this.access = access; this.documents = documents; this.payloads = payloads; this.examinations = examinations;
        this.files = files; this.clock = clock;
    }

    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public DocumentView upload(AuthenticatedAccount actor, UUID visitId, ClinicalMedicalDocumentType type, String title, DocumentFile file) {
        var scope = access.upload(actor, visitId);
        if (type == null) throw error("MSG-39");
        String name = title(title); var checked = files.validate(type, file); byte[] bytes = checked.bytes();
        UUID id = UUID.randomUUID(); var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        if (documents.insertDocument(id, visitId, scope.patientId(), type.name(), name, checked.originalFilename(),
                checked.mediaType(), bytes.length, now) != 1) throw error("DEPENDENCY_UNAVAILABLE");
        if (payloads.put(id, bytes) != 1) throw error("DEPENDENCY_UNAVAILABLE");
        return view(required(id));
    }

    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public DocumentView update(AuthenticatedAccount actor, UUID visitId, UUID documentId, Long version, String title, DocumentFile replacement) {
        var scope = access.doctor(actor, visitId); requireVersion(version);
        var document = required(documentId); requireLink(document, scope); mutable(document, version);
        String name = title(title);
        var file = replacement == null ? null : files.validate(document.getDocumentType(), replacement);
        byte[] bytes = file == null ? null : file.bytes();
        if (documents.replace(documentId, version, name, file == null ? document.getOriginalFilename() : file.originalFilename(),
                file == null ? document.getMediaType() : file.mediaType(), bytes == null ? document.getByteSize() : bytes.length,
                clock.instant().truncatedTo(ChronoUnit.MICROS)) != 1) throw error("MSG-42");
        // This write is impossible after a failed/stale header CAS. The transaction owns both changes.
        if (bytes != null && payloads.put(documentId, bytes) != 1) throw error("DEPENDENCY_UNAVAILABLE");
        return view(required(documentId));
    }

    @Override @Transactional(isolation = Isolation.READ_COMMITTED)
    public DocumentView release(AuthenticatedAccount actor, UUID visitId, UUID documentId, Long version) {
        var scope = access.doctor(actor, visitId); requireVersion(version);
        var document = required(documentId); requireLink(document, scope); mutable(document, version);
        if (document.getDocumentType() == ClinicalMedicalDocumentType.CLINIC_EXAMINATION_REPORT
                && examinations.findByVisitId(visitId).map(exam -> exam.getStatus() != ClinicalExaminationStatus.COMPLETED).orElse(true)) throw error("MSG-42");
        if (documents.release(documentId, version, scope.doctorProfileId(), clock.instant().truncatedTo(ChronoUnit.MICROS)) != 1) throw error("MSG-42");
        return view(required(documentId));
    }

    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<DocumentView> list(AuthenticatedAccount actor, UUID visitId) {
        var scope = access.read(actor, visitId);
        var result = switch (scope.role()) {
            case PATIENT -> documents.findByVisitIdAndReleasedAtIsNotNullOrderByCreatedAtDescIdDesc(visitId);
            case RECEPTIONIST -> documents.findByVisitIdAndDocumentTypeOrderByCreatedAtDescIdDesc(visitId, ClinicalMedicalDocumentType.CLINIC_EXAMINATION_REPORT);
            default -> documents.findByVisitIdOrderByCreatedAtDescIdDesc(visitId);
        };
        return result.stream().map(document -> { requireLink(document, scope); requireVisible(document, scope); return view(document); }).toList();
    }

    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DocumentView get(AuthenticatedAccount actor, UUID documentId) { return view(readable(actor, documentId)); }

    @Override @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DocumentContent content(AuthenticatedAccount actor, UUID documentId) {
        var document = readable(actor, documentId);
        var payload = payloads.findById(documentId).orElseThrow(() -> error("DEPENDENCY_UNAVAILABLE"));
        byte[] bytes = payload.getContent();
        if (bytes == null || bytes.length != document.getByteSize()) throw error("DEPENDENCY_UNAVAILABLE");
        return new DocumentContent(view(document), bytes);
    }
    private ClinicalMedicalDocumentJpaEntity readable(AuthenticatedAccount actor, UUID documentId) {
        var document = required(documentId); var scope = access.read(actor, document.getVisitId());
        requireLink(document, scope); requireVisible(document, scope); return document;
    }
    private void requireVisible(ClinicalMedicalDocumentJpaEntity document, Scope scope) {
        if (scope.role() == AccountRole.PATIENT && document.getReleasedAt() == null) throw error("MSG-41");
        if (scope.role() == AccountRole.RECEPTIONIST && document.getDocumentType() != ClinicalMedicalDocumentType.CLINIC_EXAMINATION_REPORT) throw error("MSG-40");
    }
    private void requireLink(ClinicalMedicalDocumentJpaEntity document, Scope scope) {
        if (!scope.visitId().equals(document.getVisitId()) || !scope.patientId().equals(document.getPatientProfileId())) throw error("MSG-41");
    }
    private ClinicalMedicalDocumentJpaEntity required(UUID id) {
        if (id == null) throw error("MSG-39");
        return documents.findById(id).orElseThrow(() -> error("MSG-41"));
    }
    private static void mutable(ClinicalMedicalDocumentJpaEntity document, long version) {
        if (document.getReleasedAt() != null || document.getVersion() != version) throw error("MSG-42");
    }
    private static void requireVersion(Long version) { if (version == null || version < 1 || version == Long.MAX_VALUE) throw error("MSG-39"); }
    private static String title(String value) { if (value == null || value.isBlank() || value.length() > 255) throw error("MSG-39"); return value.trim(); }
    private static DocumentView view(ClinicalMedicalDocumentJpaEntity d) {
        return new DocumentView(d.getId(), d.getVisitId(), d.getPatientProfileId(), d.getDocumentType(), d.getTitle(),
                d.getOriginalFilename(), d.getMediaType(), d.getByteSize(), d.getReleasedAt() != null, d.getReleasedAt(),
                d.getVersion(), d.getCreatedAt(), d.getUpdatedAt());
    }
    private static ClinicalOperationException error(String code) { return new ClinicalOperationException(code); }
}
