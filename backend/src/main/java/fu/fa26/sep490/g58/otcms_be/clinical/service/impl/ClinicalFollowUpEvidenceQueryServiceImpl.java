package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalExaminationStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalExaminationRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalFollowUpEvidenceQueryService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ClinicalFollowUpEvidenceQueryServiceImpl implements ClinicalFollowUpEvidenceQueryService {
    private final ClinicalExaminationRepository examinations;
    public ClinicalFollowUpEvidenceQueryServiceImpl(ClinicalExaminationRepository examinations) { this.examinations = examinations; }
    @Override public FollowUpEvidence getEvidence(UUID visitId) {
        if (visitId == null) throw new ClinicalOperationException("MSG-39");
        return examinations.findByVisitId(visitId).map(exam -> new FollowUpEvidence(visitId, true,
                exam.getStatus() == ClinicalExaminationStatus.COMPLETED, Boolean.TRUE.equals(exam.getFollowUpIndicated())))
                .orElse(new FollowUpEvidence(visitId, false, false, false));
    }
}
