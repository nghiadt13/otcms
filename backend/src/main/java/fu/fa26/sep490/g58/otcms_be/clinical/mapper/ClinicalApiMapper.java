package fu.fa26.sep490.g58.otcms_be.clinical.mapper;

import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.ClinicalDtos.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.AfterVisitAdviceService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalExaminationService;
import java.util.UUID;

public final class ClinicalApiMapper {
    private ClinicalApiMapper() { }

    public static ClinicalExaminationService.ExaminationCommand command(UUID visitId, UUID doctorId,
            ExaminationRequest body) {
        var diagnoses = body.diagnoses() == null ? null : body.diagnoses().stream()
                .map(value -> new ClinicalExaminationService.Diagnosis(value.code(), value.type())).toList();
        return new ClinicalExaminationService.ExaminationCommand(visitId, doctorId, body.reasonForVisit(),
                body.symptoms(), body.examinationFindings(), body.injuryBodySite(), body.injurySide(),
                body.injuryMechanism(), diagnoses);
    }

    public static ExaminationResponse response(ClinicalExaminationService.ExaminationView view) {
        return new ExaminationResponse(view.id(), view.visitId(), view.reasonForVisit(), view.symptoms(),
                view.examinationFindings(), view.injuryBodySite(), view.injurySide(), view.injuryMechanism(),
                view.status(), view.diagnoses().stream()
                        .map(value -> new DiagnosisResponse(value.code(), value.type())).toList(), view.version());
    }

    public static AfterVisitAdviceService.AdviceCommand command(UUID visitId, UUID doctorId, AdviceRequest body) {
        return new AfterVisitAdviceService.AdviceCommand(visitId, doctorId, body.homeCareAdvice(),
                body.activityAdvice(), body.rehabilitationAdvice(), body.warningSigns(), body.followUpRecommendation());
    }

    public static AdviceResponse response(AfterVisitAdviceService.AdviceView view) {
        return new AdviceResponse(view.visitId(), view.homeCareAdvice(), view.activityAdvice(),
                view.rehabilitationAdvice(), view.warningSigns(), view.followUpRecommendation(),
                view.examinationStatus(), view.updatedAt(), view.version());
    }
}
