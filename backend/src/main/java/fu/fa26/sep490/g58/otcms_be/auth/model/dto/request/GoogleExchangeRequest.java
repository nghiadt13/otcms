package fu.fa26.sep490.g58.otcms_be.auth.model.dto.request;


import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public class GoogleExchangeRequest {
    private @NotBlank String authorizationCode;
    private @NotBlank String codeVerifier;
    private @NotBlank String redirectUri;
    private String fullName;
    private String phone;
    private LocalDate dateOfBirth;
    private Gender gender;
    private Boolean patientConsent;
    private Boolean guardianConsent;
    private String termsVersion;
    private String privacyVersion;
    public GoogleExchangeRequest() { }
    public GoogleExchangeRequest(@NotBlank String authorizationCode, @NotBlank String codeVerifier, @NotBlank String redirectUri, String fullName, String phone, LocalDate dateOfBirth, Gender gender, Boolean patientConsent, Boolean guardianConsent, String termsVersion, String privacyVersion) {
        this.authorizationCode = authorizationCode;
        this.codeVerifier = codeVerifier;
        this.redirectUri = redirectUri;
        this.fullName = fullName;
        this.phone = phone;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.patientConsent = patientConsent;
        this.guardianConsent = guardianConsent;
        this.termsVersion = termsVersion;
        this.privacyVersion = privacyVersion;
    }
    public String getAuthorizationCode() { return authorizationCode; }
    public String authorizationCode() { return authorizationCode; }
    public void setAuthorizationCode(String authorizationCode) { this.authorizationCode = authorizationCode; }
    public String getCodeVerifier() { return codeVerifier; }
    public String codeVerifier() { return codeVerifier; }
    public void setCodeVerifier(String codeVerifier) { this.codeVerifier = codeVerifier; }
    public String getRedirectUri() { return redirectUri; }
    public String redirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
    public String getFullName() { return fullName; }
    public String fullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public String phone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public LocalDate dateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public Gender getGender() { return gender; }
    public Gender gender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }
    public Boolean getPatientConsent() { return patientConsent; }
    public Boolean patientConsent() { return patientConsent; }
    public void setPatientConsent(Boolean patientConsent) { this.patientConsent = patientConsent; }
    public Boolean getGuardianConsent() { return guardianConsent; }
    public Boolean guardianConsent() { return guardianConsent; }
    public void setGuardianConsent(Boolean guardianConsent) { this.guardianConsent = guardianConsent; }
    public String getTermsVersion() { return termsVersion; }
    public String termsVersion() { return termsVersion; }
    public void setTermsVersion(String termsVersion) { this.termsVersion = termsVersion; }
    public String getPrivacyVersion() { return privacyVersion; }
    public String privacyVersion() { return privacyVersion; }
    public void setPrivacyVersion(String privacyVersion) { this.privacyVersion = privacyVersion; }
    public GoogleExchangeRequest(String code, String verifier, String redirectUri) {
        this(code, verifier, redirectUri, null, null, null, null, null, null, null, null);
    }
    public boolean hasOnboardingData() { return fullName != null || phone != null || dateOfBirth != null || gender != null; }
}
