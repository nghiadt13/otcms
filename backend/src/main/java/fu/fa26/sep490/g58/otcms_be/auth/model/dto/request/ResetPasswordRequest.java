package fu.fa26.sep490.g58.otcms_be.auth.model.dto.request;

import jakarta.validation.constraints.*;
public class ResetPasswordRequest {
    private @NotBlank String token;
    private @NotBlank @Size(min=12,max=64) String newPassword;
    public ResetPasswordRequest() { }
    public ResetPasswordRequest(@NotBlank String token, @NotBlank @Size(min=12,max=64) String newPassword) {
        this.token = token;
        this.newPassword = newPassword;
    }
    public String getToken() { return token; }
    public String token() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getNewPassword() { return newPassword; }
    public String newPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
