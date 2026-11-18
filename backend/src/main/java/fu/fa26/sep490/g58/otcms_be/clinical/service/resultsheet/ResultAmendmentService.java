package fu.fa26.sep490.g58.otcms_be.clinical.service.resultsheet;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalExaminationStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalMedicalDocumentType;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalPrescriptionStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.DiagnosisType;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalDtos.AdviceRequest;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalDtos.AdviceResponse;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalDtos.AmendableResultResponse;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalDtos.AmendmentRequest;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalDtos.DiagnosisResponse;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalDtos.ExaminationResponse;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.PrescriptionDtos.ItemResponse;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalDtos.DiagnosisRequest;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalDtos.ExaminationRequest;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalDtos.PrescriptionAmendment;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ClinicalDiagnosisJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ClinicalExaminationJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ClinicalPrescriptionItemJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalDiagnosisRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalExaminationRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalMedicalDocumentRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalPrescriptionItemRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalPrescriptionRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalMedicalDocumentAccess;
import fu.fa26.sep490.g58.otcms_be.clinical.service.impl.ClinicalExaminationServiceImpl;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-38: amends a CLOSED visit's examination, advice and/or prescription with a mandatory reason. The previous
 * values go to audit_logs and a new result sheet version replaces the current one, all in one transaction.
 */
@Service
public class ResultAmendmentService {
    public record Amended(UUID documentId, long version) { }

    private static final int MAX_REASON = 2000;
    private final ClinicalMedicalDocumentAccess access;
    private final ClinicalExaminationRepository examinations;
    private final ClinicalDiagnosisRepository diagnoses;
    private final ClinicalPrescriptionRepository prescriptions;
    private final ClinicalPrescriptionItemRepository prescriptionItems;
    private final ClinicalMedicalDocumentRepository documents;
    private final ResultSheetIssuer issuer;
    private final AuditLogWriter audit;
    private final Clock clock;

    public ResultAmendmentService(ClinicalMedicalDocumentAccess access, ClinicalExaminationRepository examinations,
            ClinicalDiagnosisRepository diagnoses, ClinicalPrescriptionRepository prescriptions,
            ClinicalPrescriptionItemRepository prescriptionItems, ClinicalMedicalDocumentRepository documents,
            ResultSheetIssuer issuer, AuditLogWriter audit, Clock clock) {
        this.access = access; this.examinations = examinations; this.diagnoses = diagnoses;
        this.prescriptions = prescriptions; this.prescriptionItems = prescriptionItems; this.documents = documents;
        this.issuer = issuer; this.audit = audit; this.clock = clock;
    }

    @Transactional
    public Amended amend(AuthenticatedAccount actor, UUID visitId, AmendmentRequest request) {
        var scope = access.amendResult(actor, visitId);
        if (request == null) throw error("MSG-39");
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.isEmpty() || reason.length() > MAX_REASON) throw error("MSG-39");
        if (request.examination() == null && request.advice() == null && request.prescription() == null) throw error("MSG-39");

        Map<String, Object> before = new LinkedHashMap<>(), after = new LinkedHashMap<>();
        var exam = examinations.findByVisitId(visitId).filter(e -> e.getStatus() == ClinicalExaminationStatus.COMPLETED)
                .orElseThrow(() -> error("MSG-42"));
        var now = clock.instant();
        if (request.examination() != null) amendExamination(exam, request.examination(), before, after, now);
        if (request.advice() != null) amendAdvice(exam, request.advice(), before, after, now);
        examinations.saveAndFlush(exam);
        if (request.prescription() != null) amendPrescription(visitId, request.prescription(), before, after);

        long version = issuer.reissue(visitId, scope.doctorProfileId());
        UUID documentId = documents.findByVisitIdAndDocumentTypeOrderByCreatedAtDescIdDesc(visitId,
                ClinicalMedicalDocumentType.CLINIC_EXAMINATION_REPORT).stream()
                .filter(document -> "ACTIVE".equals(document.getStatus())).findFirst().orElseThrow().getId();
        after.put("resultSheetVersion", version);
        audit.record(AuditEntry.builder("clinical_result_amendments", "CLINICAL", "RESULT_AMENDED", "VISIT")
                .entityId(visitId).recordLabel(visitId.toString()).correctionReason(reason)
                .actorAccountId(actor.accountId()).actorType("ACCOUNT").beforeState(before).afterState(after)
                .metadata(Map.of("visitId", visitId.toString(), "newResultSheetVersion", version)).build());
        return new Amended(documentId, version);
    }

    private void amendExamination(ClinicalExaminationJpaEntity exam, ExaminationRequest body, Map<String, Object> before,
            Map<String, Object> after, java.time.Instant now) {
        var newDiagnoses = body.diagnoses() == null ? List.<DiagnosisRequest>of() : body.diagnoses();
        if (blank(body.reasonForVisit()) || blank(body.symptoms()) || blank(body.examinationFindings())
                || newDiagnoses.stream().filter(d -> d != null && d.type() == DiagnosisType.PRIMARY).count() != 1
                || newDiagnoses.stream().anyMatch(d -> d == null || d.type() == null || d.code() == null
                        || !ClinicalExaminationServiceImpl.ICD.matcher(d.code()).matches())) throw error("MSG-39");
        var oldDiagnoses = diagnoses.findByExaminationId(exam.getId());
        before.put("examination", examinationState(exam.getReasonForVisit(), exam.getSymptoms(), exam.getExaminationFindings(),
                exam.getInjuryBodySite(), exam.getInjurySide(), exam.getInjuryMechanism(),
                oldDiagnoses.stream().map(d -> d.getCode() + ":" + d.getType()).toList()));
        exam.update(body.reasonForVisit(), body.symptoms(), body.examinationFindings(), body.injuryBodySite(),
                body.injurySide(), body.injuryMechanism(), ClinicalExaminationStatus.COMPLETED, now);
        diagnoses.deleteByExaminationId(exam.getId());
        diagnoses.flush();
        var saved = new ArrayList<String>();
        for (var d : newDiagnoses) {
            String code = d.code().toUpperCase(Locale.ROOT);
            diagnoses.save(new ClinicalDiagnosisJpaEntity(exam.getId(), code, d.type()));
            saved.add(code + ":" + d.type());
        }
        after.put("examination", examinationState(body.reasonForVisit(), body.symptoms(), body.examinationFindings(),
                body.injuryBodySite(), body.injurySide(), body.injuryMechanism(), saved));
    }

    private void amendAdvice(ClinicalExaminationJpaEntity exam, AdviceRequest body, Map<String, Object> before,
            Map<String, Object> after, java.time.Instant now) {
        if (blank(body.homeCareAdvice()) && blank(body.activityAdvice()) && blank(body.rehabilitationAdvice())
                && blank(body.warningSigns()) && blank(body.followUpRecommendation())) throw error("MSG-39");
        before.put("advice", adviceState(exam.getHomeCareAdvice(), exam.getActivityAdvice(), exam.getRehabilitationAdvice(),
                exam.getWarningSigns(), exam.getFollowUpRecommendation(), exam.getFollowUpIndicated()));
        exam.updateAfterVisitAdvice(body.homeCareAdvice(), body.activityAdvice(), body.rehabilitationAdvice(),
                body.warningSigns(), body.followUpRecommendation(), now);
        if (body.followUpIndicated() != null) exam.setFollowUpIndicated(body.followUpIndicated());
        after.put("advice", adviceState(body.homeCareAdvice(), body.activityAdvice(), body.rehabilitationAdvice(),
                body.warningSigns(), body.followUpRecommendation(), exam.getFollowUpIndicated()));
    }

    private void amendPrescription(UUID visitId, PrescriptionAmendment body, Map<String, Object> before, Map<String, Object> after) {
        if (body.items() == null || body.items().isEmpty()) throw error("MSG-39");
        var prescription = prescriptions.findByVisitId(visitId).filter(p -> p.getStatus() == ClinicalPrescriptionStatus.ISSUED)
                .orElseThrow(() -> error("MSG-42"));
        var old = prescriptionItems.findByPrescriptionIdOrderByLineNumberAsc(prescription.getId());
        before.put("prescription", old.stream().map(i -> List.of(i.getMedicine(), i.getDose(), i.getFrequency(), i.getUsage())).toList());
        prescriptionItems.deleteItems(prescription.getId());
        var entities = new ArrayList<ClinicalPrescriptionItemJpaEntity>();
        var summary = new ArrayList<List<String>>();
        for (int index = 0; index < body.items().size(); index++) {
            var line = body.items().get(index);
            entities.add(new ClinicalPrescriptionItemJpaEntity(prescription.getId(), index + 1, line.medicine().trim(),
                    line.dose().trim(), line.frequency().trim(), line.usage().trim()));
            summary.add(List.of(line.medicine().trim(), line.dose().trim(), line.frequency().trim(), line.usage().trim()));
        }
        prescriptionItems.saveAllAndFlush(entities);
        after.put("prescription", summary);
    }

    private static Map<String, Object> examinationState(String reason, String symptoms, String findings, String site,
            String side, String mechanism, List<String> diagnosisCodes) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("reasonForVisit", reason); state.put("symptoms", symptoms); state.put("examinationFindings", findings);
        state.put("injuryBodySite", site); state.put("injurySide", side); state.put("injuryMechanism", mechanism);
        state.put("diagnoses", diagnosisCodes);
        return state;
    }

    private static Map<String, Object> adviceState(String homeCare, String activity, String rehabilitation, String warnings,
            String followUp, Boolean followUpIndicated) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("homeCareAdvice", homeCare); state.put("activityAdvice", activity);
        state.put("rehabilitationAdvice", rehabilitation); state.put("warningSigns", warnings);
        state.put("followUpRecommendation", followUp); state.put("followUpIndicated", followUpIndicated);
        return state;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static ClinicalOperationException error(String code) { return new ClinicalOperationException(code); }

    /** Current examination, advice and issued prescription of a CLOSED visit; same access rules as {@link #amend}. */
    @Transactional(readOnly = true)
    public AmendableResultResponse current(AuthenticatedAccount actor, UUID visitId) {
        access.amendResult(actor, visitId);
        var exam = examinations.findByVisitId(visitId).filter(e -> e.getStatus() == ClinicalExaminationStatus.COMPLETED)
                .orElseThrow(() -> error("MSG-41"));
        var examination = new ExaminationResponse(exam.getId(), visitId, exam.getReasonForVisit(), exam.getSymptoms(),
                exam.getExaminationFindings(), exam.getInjuryBodySite(), exam.getInjurySide(), exam.getInjuryMechanism(),
                exam.getStatus(), diagnoses.findByExaminationId(exam.getId()).stream()
                        .map(d -> new DiagnosisResponse(d.getCode(), d.getType())).toList(), exam.getVersion());
        var advice = new AdviceResponse(visitId, exam.getHomeCareAdvice(), exam.getActivityAdvice(), exam.getRehabilitationAdvice(),
                exam.getWarningSigns(), exam.getFollowUpRecommendation(), exam.getStatus(), exam.getUpdatedAt(), exam.getVersion(),
                exam.getFollowUpIndicated());
        var items = prescriptions.findByVisitId(visitId).filter(p -> p.getStatus() == ClinicalPrescriptionStatus.ISSUED)
                .map(p -> prescriptionItems.findByPrescriptionIdOrderByLineNumberAsc(p.getId()).stream()
                        .map(i -> new ItemResponse(i.getLineNumber(), i.getMedicine(), i.getDose(), i.getFrequency(), i.getUsage())).toList())
                .orElse(List.of());
        Long sheetVersion = documents.findByVisitIdAndDocumentTypeOrderByCreatedAtDescIdDesc(visitId,
                ClinicalMedicalDocumentType.CLINIC_EXAMINATION_REPORT).stream()
                .filter(document -> "ACTIVE".equals(document.getStatus())).map(document -> document.getVersion()).findFirst().orElse(null);
        return new AmendableResultResponse(examination, advice, items, sheetVersion);
    }
}
