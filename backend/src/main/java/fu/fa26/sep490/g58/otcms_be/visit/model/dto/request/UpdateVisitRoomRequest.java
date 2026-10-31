package fu.fa26.sep490.g58.otcms_be.visit.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UpdateVisitRoomRequest {
    private String displayLabel;

    @Size(max = 32)
    private List<@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String> capabilities = new ArrayList<>();

    @Size(max = 1000)
    private String description;

    @Size(max = 32)
    private List<@NotNull UUID> serviceIds;

    public String getDisplayLabel() { return displayLabel; }
    public void setDisplayLabel(String displayLabel) { this.displayLabel = displayLabel; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getCapabilities() { return capabilities; }
    public void setCapabilities(List<String> capabilities) { this.capabilities = capabilities; }
    public List<UUID> getServiceIds() { return serviceIds; }
    public void setServiceIds(List<UUID> serviceIds) { this.serviceIds = serviceIds; }
}
