package fu.fa26.sep490.g58.otcms_be.auth.model.dto.request;

import jakarta.validation.constraints.NotBlank;
public class ForgotPasswordRequest {
    private @NotBlank String email;
    public ForgotPasswordRequest() { }
    public ForgotPasswordRequest(@NotBlank String email) {
        this.email = email;
    }
    public String getEmail() { return email; }
    public String email() { return email; }
    public void setEmail(String email) { this.email = email; }
}
