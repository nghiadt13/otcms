package fu.fa26.sep490.g58.otcms_be.auth.model.dto.request;

import fu.fa26.sep490.g58.otcms_be.common.api.StrictRequestBody;
import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.JsonNode;

public class CreateAdministratorAccountRequest extends StrictRequestBody {
    @NotBlank
    private String username;

    public String getUsername() {
        return username;
    }

    public void setUsername(JsonNode username) {
        this.username = strictText(username);
    }
}
