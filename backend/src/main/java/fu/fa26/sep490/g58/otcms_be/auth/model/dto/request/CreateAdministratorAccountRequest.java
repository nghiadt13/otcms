package fu.fa26.sep490.g58.otcms_be.auth.model.dto.request;

import fu.fa26.sep490.g58.otcms_be.common.api.StrictRequestBody;
import fu.fa26.sep490.g58.otcms_be.auth.validation.AdministratorUsernamePolicy;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public class CreateAdministratorAccountRequest extends StrictRequestBody {
    @NotBlank
    @Size(max = AdministratorUsernamePolicy.MAX_LENGTH)
    private String username;

    @AssertTrue(message = "Invalid administrator username")
    public boolean isUsernameValid() {
        return AdministratorUsernamePolicy.isValid(username);
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(JsonNode username) {
        this.username = strictText(username);
    }
}
