package fu.fa26.sep490.g58.otcms_be.imaging.model.dto.request;

import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingReconciliationDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ImagingReconciliationDecisionRequest(
        @NotNull ImagingReconciliationDecision decision,
        @NotBlank @Pattern(regexp = "INC-[0-9]{1,12}") String resolutionRef) { }
