package fu.fa26.sep490.g58.otcms_be.auth.model.dto.request;

import jakarta.validation.constraints.NotBlank;
public class CredentialLoginRequest {
    private @NotBlank String email;
    private @NotBlank String password;
    /** Không gửi thì coi như ghi nhớ (giữ hành vi cũ của các client chưa có ô này). */
    private Boolean rememberMe;
    public CredentialLoginRequest() { }
    public CredentialLoginRequest(@NotBlank String email, @NotBlank String password) {
        this.email = email;
        this.password = password;
    }
    public String getEmail() { return email; }
    public String email() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public String password() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Boolean getRememberMe() { return rememberMe; }
    public void setRememberMe(Boolean rememberMe) { this.rememberMe = rememberMe; }
    public boolean rememberMeOrDefault() { return rememberMe == null || rememberMe; }
}
