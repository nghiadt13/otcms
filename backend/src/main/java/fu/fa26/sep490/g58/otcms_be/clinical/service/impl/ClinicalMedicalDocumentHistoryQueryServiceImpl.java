package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalMedicalDocumentRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalMedicalDocumentHistoryQueryService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true)
public class ClinicalMedicalDocumentHistoryQueryServiceImpl implements ClinicalMedicalDocumentHistoryQueryService {
    private final ClinicalMedicalDocumentRepository documents;
    public ClinicalMedicalDocumentHistoryQueryServiceImpl(ClinicalMedicalDocumentRepository documents) { this.documents = documents; }
    @Override public List<DocumentSummary> findReleased(UUID patientId, Collection<UUID> visitIds) {
        if (patientId == null || visitIds == null || visitIds.size() > 50 || visitIds.stream().anyMatch(Objects::isNull)) {
            throw new ClinicalOperationException("MSG-39");
        }
        if (visitIds.isEmpty()) return List.of();
        return documents.findByPatientProfileIdAndVisitIdInAndReleasedAtIsNotNullOrderByCreatedAtDescIdDesc(patientId, visitIds)
                .stream().map(d -> new DocumentSummary(d.getId(), d.getVisitId(), d.getPatientProfileId(),
                        d.getDocumentType().name(), d.getTitle(), d.getMediaType(), d.getReleasedAt())).toList();
    }
}
