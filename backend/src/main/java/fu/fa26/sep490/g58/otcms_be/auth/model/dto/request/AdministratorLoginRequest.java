package fu.fa26.sep490.g58.otcms_be.auth.model.dto.request;

import fu.fa26.sep490.g58.otcms_be.common.api.StrictRequestBody;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdministratorLoginRequest extends StrictRequestBody {
    @NotBlank
    private String username;
    @NotBlank
    private String password;
}
