package fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import fu.fa26.sep490.g58.otcms_be.common.api.StrictRequestBody;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public class ReassignAppointmentRequest extends StrictRequestBody {
    @NotNull private UUID doctorId;
    @NotBlank @Size(max = 1002) private String reason;

    public UUID getDoctorId() { return doctorId; }
    public void setDoctorId(JsonNode doctorId) { this.doctorId = strictUuid(doctorId); }
    public String getReason() { return reason; }
    public void setReason(JsonNode reason) { this.reason = strictText(reason); }
}
