package fu.fa26.sep490.g58.otcms_be.servicecatalog.model.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class DoctorAssignmentRequest {
    @NotNull private UUID doctorProfileId;
    public DoctorAssignmentRequest() { }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public void setDoctorProfileId(UUID value) { doctorProfileId=value; }
}
