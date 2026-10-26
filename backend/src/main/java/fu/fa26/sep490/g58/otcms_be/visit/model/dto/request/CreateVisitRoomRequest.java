package fu.fa26.sep490.g58.otcms_be.visit.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

public class CreateVisitRoomRequest {
    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]{0,63}")
    private String roomCode;

    @NotBlank
    @Size(max = 128)
    private String displayLabel;

    @NotEmpty
    @Size(max = 32)
    private List<@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String> capabilities = new ArrayList<>();

    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }
    public String getDisplayLabel() { return displayLabel; }
    public void setDisplayLabel(String displayLabel) { this.displayLabel = displayLabel; }
    public List<String> getCapabilities() { return capabilities; }
    public void setCapabilities(List<String> capabilities) { this.capabilities = capabilities; }
}
