package fu.fa26.sep490.g58.otcms_be.auth.dto;

import fu.fa26.sep490.g58.otcms_be.auth.entity.Gender;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record RegistrationRequest(
        @NotBlank @Size(min = 2, max = 100) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank String phone,
        @NotNull @PastOrPresent LocalDate dateOfBirth,
        @NotNull Gender gender,
        @NotBlank @Size(min = 1, max = 64) String password,
        @NotBlank String confirmPassword,
        @AssertTrue boolean patientConsent,
        boolean guardianConsent) { }
