package fu.fa26.sep490.g58.otcms_be.auth.model.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegistrationRequest {
    @NotBlank @Size(min = 2, max = 100)
    private String fullName;
    @NotBlank @Email @Size(max = 254)
    private String email;
    @NotBlank @Size(min = 8, max = 64)
    private String password;
    @NotBlank @Size(max = 64)
    private String confirmPassword;
    @AssertTrue
    private boolean patientConsent;

    public RegistrationRequest() { }

    public RegistrationRequest(String fullName, String email, String password,
                               String confirmPassword, boolean patientConsent) {
        this.fullName = fullName;
        this.email = email;
        this.password = password;
        this.confirmPassword = confirmPassword;
        this.patientConsent = patientConsent;
    }

    public String getFullName() { return fullName; }
    public String fullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public String email() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public String password() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmPassword() { return confirmPassword; }
    public String confirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    public boolean getPatientConsent() { return patientConsent; }
    public boolean isPatientConsent() { return patientConsent; }
    public boolean patientConsent() { return patientConsent; }
    public void setPatientConsent(boolean patientConsent) { this.patientConsent = patientConsent; }
}
