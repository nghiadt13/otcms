package fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request;

import fu.fa26.sep490.g58.otcms_be.common.api.StrictRequestBody;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** HTTP request for a Doctor to book a follow-up from a qualifying Visit. */
public class CreateFollowUpAppointmentRequest extends StrictRequestBody {
    @NotNull
    @Schema(format = "uuid")
    private UUID serviceId;

    @NotNull
    @Schema(format = "uuid")
    private UUID doctorId;

    @NotNull
    @Schema(format = "date-time")
    private OffsetDateTime startTime;

    @NotBlank
    @Size(max = 1000)
    private String purpose;

    @NotBlank
    @Size(max = 1000)
    private String reason;

    public UUID getServiceId() {
        return serviceId;
    }

    public void setServiceId(JsonNode value) {
        serviceId = strictUuid(value);
    }

    public UUID getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(JsonNode value) {
        doctorId = strictUuid(value);
    }

    public OffsetDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(JsonNode value) {
        startTime = strictOffsetDateTime(value);
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(JsonNode value) {
        purpose = strictText(value);
    }

    public String getReason() {
        return reason;
    }

    public void setReason(JsonNode value) {
        reason = strictText(value);
    }
}
