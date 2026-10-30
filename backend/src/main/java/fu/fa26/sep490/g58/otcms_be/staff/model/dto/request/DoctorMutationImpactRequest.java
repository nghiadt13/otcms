package fu.fa26.sep490.g58.otcms_be.staff.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public class DoctorMutationImpactRequest {
    @NotBlank private String resourceType;
    private UUID resourceId;
    @NotNull private Instant startsAt;
    @NotNull private Instant endsAt;
    public DoctorMutationImpactRequest() { }
    public String getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public void setResourceType(String value) { resourceType=value; }
    public void setResourceId(UUID value) { resourceId=value; }
    public void setStartsAt(Instant value) { startsAt=value; }
    public void setEndsAt(Instant value) { endsAt=value; }
}
