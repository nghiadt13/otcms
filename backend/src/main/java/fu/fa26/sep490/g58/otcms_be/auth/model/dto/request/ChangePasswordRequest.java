package fu.fa26.sep490.g58.otcms_be.auth.model.dto.request;

import jakarta.validation.constraints.*;
public class ChangePasswordRequest {
    private @NotBlank String currentPassword;
    private @NotBlank @Size(min=12,max=64) String newPassword;
    public ChangePasswordRequest() { }
    public ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min=12,max=64) String newPassword) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
    }
    public String getCurrentPassword() { return currentPassword; }
    public String currentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
    public String getNewPassword() { return newPassword; }
    public String newPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
