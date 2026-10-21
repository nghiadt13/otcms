package fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request;

import jakarta.validation.constraints.NotNull;
import fu.fa26.sep490.g58.otcms_be.common.api.StrictRequestBody;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import tools.jackson.databind.JsonNode;

public class RescheduleAppointmentRequest extends StrictRequestBody {
    @NotNull private OffsetDateTime startTime;
    @Size(max = 1002) private String reason;

    public OffsetDateTime getStartTime() { return startTime; }
    public void setStartTime(JsonNode startTime) { this.startTime = strictOffsetDateTime(startTime); }
    public String getReason() { return reason; }
    public void setReason(JsonNode reason) { this.reason = strictText(reason); }
}
