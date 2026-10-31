package fu.fa26.sep490.g58.otcms_be.visit.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType;

public class CreateVisitRoomRequest {
    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "[\\p{L}\\p{N}][\\p{L}\\p{N}_-]{0,63}")
    private String roomCode;

    @NotBlank
    @Size(max = 128)
    private String displayLabel;

    @Size(max = 32)
    private List<@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String> capabilities = new ArrayList<>();

    private ClinicServiceType type;

    @Size(max = 1000)
    private String description = "";

    @Size(max = 32)
    private List<@NotNull UUID> serviceIds;

    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }
    public String getDisplayLabel() { return displayLabel; }
    public void setDisplayLabel(String displayLabel) { this.displayLabel = displayLabel; }
    public List<String> getCapabilities() { return capabilities; }
    public void setCapabilities(List<String> capabilities) { this.capabilities = capabilities; }
    public ClinicServiceType getType() { return type; }
    public void setType(ClinicServiceType type) { this.type = type; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<UUID> getServiceIds() { return serviceIds; }
    public void setServiceIds(List<UUID> serviceIds) { this.serviceIds = serviceIds; }
}
