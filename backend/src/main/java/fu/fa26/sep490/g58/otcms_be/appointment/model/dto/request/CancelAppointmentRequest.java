package fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request;

import jakarta.validation.constraints.Size;
import fu.fa26.sep490.g58.otcms_be.common.api.StrictRequestBody;
import tools.jackson.databind.JsonNode;

public class CancelAppointmentRequest extends StrictRequestBody {
    @Size(max = 1002) private String reason;

    public String getReason() { return reason; }
    public void setReason(JsonNode reason) { this.reason = strictText(reason); }
}
