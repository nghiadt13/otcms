package fu.fa26.sep490.g58.otcms_be.servicecatalog.model.dto.response;

import java.util.UUID;

public class DoctorAssignmentResponse {
    private final UUID id;
    private final UUID serviceId;
    private final UUID doctorProfileId;
    public DoctorAssignmentResponse(UUID id, UUID serviceId, UUID doctorProfileId) {
        this.id=id; this.serviceId=serviceId; this.doctorProfileId=doctorProfileId;
    }
    public UUID getId() { return id; }
    public UUID getServiceId() { return serviceId; }
    public UUID getDoctorProfileId() { return doctorProfileId; }
}
