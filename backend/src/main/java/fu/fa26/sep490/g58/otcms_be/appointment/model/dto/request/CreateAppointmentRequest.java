package fu.fa26.sep490.g58.otcms_be.appointment.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import fu.fa26.sep490.g58.otcms_be.common.api.StrictRequestBody;
import java.time.OffsetDateTime;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public class CreateAppointmentRequest extends StrictRequestBody {
    private UUID patientProfileId;
    @NotNull private UUID serviceId;
    @NotNull private UUID doctorId;
    @NotNull private OffsetDateTime startTime;
    @NotBlank @Size(max = 1002) private String purpose;

    public UUID getPatientProfileId() { return patientProfileId; }
    public void setPatientProfileId(JsonNode patientProfileId) { this.patientProfileId = strictUuid(patientProfileId); }
    public UUID getServiceId() { return serviceId; }
    public void setServiceId(JsonNode serviceId) { this.serviceId = strictUuid(serviceId); }
    public UUID getDoctorId() { return doctorId; }
    public void setDoctorId(JsonNode doctorId) { this.doctorId = strictUuid(doctorId); }
    public OffsetDateTime getStartTime() { return startTime; }
    public void setStartTime(JsonNode startTime) { this.startTime = strictOffsetDateTime(startTime); }
    public String getPurpose() { return purpose; }
    public void setPurpose(JsonNode purpose) { this.purpose = strictText(purpose); }
}
