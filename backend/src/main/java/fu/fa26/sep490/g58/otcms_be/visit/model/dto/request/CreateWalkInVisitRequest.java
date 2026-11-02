package fu.fa26.sep490.g58.otcms_be.visit.model.dto.request;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import fu.fa26.sep490.g58.otcms_be.common.api.StrictRequestBody;

@Schema(name = "WalkInRequest")
public class CreateWalkInVisitRequest extends StrictRequestBody {
    @NotNull
    private UUID patientProfileId;
    private UUID priorAppointmentId;

    public CreateWalkInVisitRequest() { }

    public UUID getPatientProfileId() { return patientProfileId; }
    public void setPatientProfileId(UUID patientProfileId) { this.patientProfileId = patientProfileId; }
    @Schema(nullable = true)
    public UUID getPriorAppointmentId() { return priorAppointmentId; }
    public void setPriorAppointmentId(UUID priorAppointmentId) { this.priorAppointmentId = priorAppointmentId; }
}
