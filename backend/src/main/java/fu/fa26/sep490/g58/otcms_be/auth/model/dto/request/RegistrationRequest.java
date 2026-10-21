package fu.fa26.sep490.g58.otcms_be.auth.model.dto.request;


import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.Gender;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public class RegistrationRequest {
    private @NotBlank @Size(min = 2, max = 100) String fullName;
    private @NotBlank @Email @Size(max = 254) String email;
    private @NotBlank String phone;
    private @NotNull @PastOrPresent LocalDate dateOfBirth;
    private @NotNull Gender gender;
    private @NotBlank @Size(min = 1, max = 64) String password;
    private @NotBlank String confirmPassword;
    private @AssertTrue boolean patientConsent;
    private boolean guardianConsent;
    public RegistrationRequest() { }
    public RegistrationRequest(@NotBlank @Size(min = 2, max = 100) String fullName, @NotBlank @Email @Size(max = 254) String email, @NotBlank String phone, @NotNull @PastOrPresent LocalDate dateOfBirth, @NotNull Gender gender, @NotBlank @Size(min = 1, max = 64) String password, @NotBlank String confirmPassword, @AssertTrue boolean patientConsent, boolean guardianConsent) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.password = password;
        this.confirmPassword = confirmPassword;
        this.patientConsent = patientConsent;
        this.guardianConsent = guardianConsent;
    }
    public String getFullName() { return fullName; }
    public String fullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public String email() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public String phone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public LocalDate dateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public Gender getGender() { return gender; }
    public Gender gender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }
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
    public boolean getGuardianConsent() { return guardianConsent; }
    public boolean isGuardianConsent() { return guardianConsent; }
    public boolean guardianConsent() { return guardianConsent; }
    public void setGuardianConsent(boolean guardianConsent) { this.guardianConsent = guardianConsent; }
}
