package fu.fa26.sep490.g58.otcms_be.auth.model.dto.request;

import jakarta.validation.constraints.*;
public class EmailRequest {
    private @NotBlank @Email @Size(max = 254) String email;
    public EmailRequest() { }
    public EmailRequest(@NotBlank @Email @Size(max = 254) String email) {
        this.email = email;
    }
    public String getEmail() { return email; }
    public String email() { return email; }
    public void setEmail(String email) { this.email = email; }
}
