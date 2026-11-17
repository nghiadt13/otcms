package fu.fa26.sep490.g58.otcms_be.clinical.service.resultsheet;

import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalMedicalDocumentType;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalMedicalDocumentRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalMedicalDocumentAccess;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResultSheetQueryService {
    public record ResultSheet(UUID documentId, long version, String status, Instant releasedAt) { }
    private final ClinicalMedicalDocumentAccess access;
    private final ClinicalMedicalDocumentRepository documents;
    private final ResultSheetIssuer issuer;

    public ResultSheetQueryService(ClinicalMedicalDocumentAccess access, ClinicalMedicalDocumentRepository documents,
            ResultSheetIssuer issuer) {
        this.access = access; this.documents = documents; this.issuer = issuer;
    }

    @Transactional(readOnly = true)
    public List<ResultSheet> list(AuthenticatedAccount actor, UUID visitId) {
        var scope = access.resultSheet(actor, visitId, true);
        return documents.findByVisitIdAndDocumentTypeOrderByCreatedAtDescIdDesc(visitId,
                ClinicalMedicalDocumentType.CLINIC_EXAMINATION_REPORT).stream()
                .filter(document -> scope.patientId().equals(document.getPatientProfileId()))
                .filter(document -> scope.role() != AccountRole.PATIENT || document.getReleasedAt() != null)
                .map(document -> new ResultSheet(document.getId(), document.getVersion(), document.getStatus(), document.getReleasedAt()))
                .toList();
    }

    public List<ResultSheet> regenerate(AuthenticatedAccount actor, UUID visitId) {
        var scope = access.regenerateResultSheet(actor, visitId);
        issuer.issue(visitId, scope.doctorProfileId());
        return list(actor, visitId);
    }
}
