package fu.fa26.sep490.g58.otcms_be.visit.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CareStepAssignmentRequest {
    @NotBlank
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private String stepCode;

    public CareStepAssignmentRequest() { }

    public String getStepCode() { return stepCode; }

    public void setStepCode(String stepCode) { this.stepCode = stepCode; }
}
