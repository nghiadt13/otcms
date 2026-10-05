package fu.fa26.sep490.g58.otcms_be.auth.dto;

import fu.fa26.sep490.g58.otcms_be.auth.entity.Gender;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record GoogleExchangeRequest(@NotBlank String authorizationCode,
                                    @NotBlank String codeVerifier,
                                    @NotBlank String redirectUri,
                                    String fullName, String phone, LocalDate dateOfBirth, Gender gender,
                                    Boolean patientConsent, Boolean guardianConsent,
                                    String termsVersion, String privacyVersion) {
    public GoogleExchangeRequest(String code, String verifier, String redirectUri) {
        this(code, verifier, redirectUri, null, null, null, null, null, null, null, null);
    }
    public boolean hasOnboardingData() { return fullName != null || phone != null || dateOfBirth != null || gender != null; }
}
