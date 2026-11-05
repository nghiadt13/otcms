package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalExaminationStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ClinicalDiagnosisJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalExaminationHistoryQueryService;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true)
public class ClinicalExaminationHistoryQueryServiceImpl implements ClinicalExaminationHistoryQueryService {
    private final ClinicalExaminationRepository examinations;
    private final ClinicalDiagnosisRepository diagnoses;
    public ClinicalExaminationHistoryQueryServiceImpl(ClinicalExaminationRepository examinations, ClinicalDiagnosisRepository diagnoses) {
        this.examinations = examinations; this.diagnoses = diagnoses;
    }
    @Override public Map<UUID, CompletedRecord> findCompletedByVisitIds(Collection<UUID> visitIds) {
        if (visitIds == null || visitIds.size() > 50 || visitIds.stream().anyMatch(Objects::isNull)) throw new ClinicalOperationException("MSG-39");
        if (visitIds.isEmpty()) return Map.of();
        var exams = examinations.findByVisitIdInAndStatus(visitIds, ClinicalExaminationStatus.COMPLETED);
        if (exams.isEmpty()) return Map.of();
        var grouped = diagnoses.findByExaminationIdIn(exams.stream().map(e -> e.getId()).toList()).stream()
                .collect(Collectors.groupingBy(ClinicalDiagnosisJpaEntity::getExaminationId));
        Map<UUID, CompletedRecord> result = new LinkedHashMap<>();
        for (var exam : exams) {
            var lines = grouped.getOrDefault(exam.getId(), List.of()).stream()
                    .sorted(Comparator.comparing((ClinicalDiagnosisJpaEntity d) -> d.getType().name()).thenComparing(ClinicalDiagnosisJpaEntity::getCode))
                    .map(d -> new Diagnosis(d.getCode(), d.getType())).toList();
            var advice = new Advice(exam.getHomeCareAdvice(), exam.getActivityAdvice(), exam.getRehabilitationAdvice(),
                    exam.getWarningSigns(), exam.getFollowUpRecommendation(), exam.getFollowUpIndicated());
            var examination = new Examination(exam.getReasonForVisit(), exam.getSymptoms(), exam.getExaminationFindings(),
                    exam.getInjuryBodySite(), exam.getInjurySide(), exam.getInjuryMechanism(), lines);
            result.put(exam.getVisitId(), new CompletedRecord(exam.getVisitId(), examination, advice));
        }
        return Map.copyOf(result);
    }
}
