package fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import fu.fa26.sep490.g58.otcms_be.common.api.StrictRequestBody;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public class UpdateAppointmentStatusRequest extends StrictRequestBody {
    @NotBlank private String targetStatus;
    @Size(max = 1002) private String reason;
    private boolean reasonPropertyPresent;

    public String getTargetStatus() { return targetStatus; }
    public void setTargetStatus(JsonNode targetStatus) { this.targetStatus = strictText(targetStatus); }
    public String getReason() { return reason; }
    public void setReason(JsonNode reason) { this.reasonPropertyPresent = true; this.reason = strictText(reason); }
    public boolean hasReasonProperty() { return reasonPropertyPresent; }
}
