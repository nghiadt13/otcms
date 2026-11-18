package fu.fa26.sep490.g58.otcms_be.clinical.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalExaminationStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.DiagnosisType;
import fu.fa26.sep490.g58.otcms_be.clinical.model.dto.PrescriptionDtos.ItemRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ClinicalDtos {
    private ClinicalDtos() { }

    // Unknown properties are ignored; caller-supplied identities/status never enter commands.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExaminationRequest(String reasonForVisit, String symptoms, String examinationFindings,
            @Size(max = 120) String injuryBodySite, @Size(max = 24) String injurySide,
            String injuryMechanism, List<@NotNull @Valid DiagnosisRequest> diagnoses) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DiagnosisRequest(String code, DiagnosisType type) { }

    public record DiagnosisResponse(String code, DiagnosisType type) { }

    public record ExaminationResponse(UUID id, UUID visitId, String reasonForVisit, String symptoms,
            String examinationFindings, String injuryBodySite, String injurySide, String injuryMechanism,
            ClinicalExaminationStatus status, List<DiagnosisResponse> diagnoses, Long version) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdviceRequest(String homeCareAdvice, String activityAdvice, String rehabilitationAdvice,
            String warningSigns, String followUpRecommendation, Boolean followUpIndicated) {
        public AdviceRequest(String homeCareAdvice, String activityAdvice, String rehabilitationAdvice,
                String warningSigns, String followUpRecommendation) {
            this(homeCareAdvice, activityAdvice, rehabilitationAdvice, warningSigns, followUpRecommendation, null);
        }
    }

    public record AdviceResponse(UUID visitId, String homeCareAdvice, String activityAdvice,
            String rehabilitationAdvice, String warningSigns, String followUpRecommendation,
            ClinicalExaminationStatus examinationStatus, Instant updatedAt, Long version, Boolean followUpIndicated) { }

    /**
     * UC-38 amendment. A section that is present replaces the stored section as a whole; an absent section is left
     * untouched. {@code reason} is mandatory (BR-47) and is validated by the service so that MSG-39 is returned.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AmendmentRequest(@Valid ExaminationRequest examination, @Valid AdviceRequest advice,
            @Valid PrescriptionAmendment prescription, @Size(max = 2000) String reason) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PrescriptionAmendment(List<@NotNull @Valid ItemRequest> items) { }

    public record AmendmentResponse(UUID documentId, long version) { }

    /** UC-38 step 2: current values of a closed visit, so the doctor can edit them. */
    public record AmendableResultResponse(ExaminationResponse examination, AdviceResponse advice,
            List<PrescriptionDtos.ItemResponse> prescriptionItems, Long resultSheetVersion) { }
}
