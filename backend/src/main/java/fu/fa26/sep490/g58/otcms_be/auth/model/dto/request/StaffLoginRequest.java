package fu.fa26.sep490.g58.otcms_be.auth.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;

/** Accepts username for current staff accounts and email for older accounts. */
public class StaffLoginRequest {
    private String username;
    private String email;
    @NotBlank private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String identifier() { return username != null && !username.isBlank() ? username : email; }
    @AssertTrue public boolean isIdentifierValid() {
        return (username != null && !username.isBlank()) != (email != null && !email.isBlank());
    }
}
