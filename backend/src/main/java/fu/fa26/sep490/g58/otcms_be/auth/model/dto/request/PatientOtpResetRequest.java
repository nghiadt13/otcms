package fu.fa26.sep490.g58.otcms_be.auth.model.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PatientOtpResetRequest(@NotBlank @Email String email, @NotBlank String otp,
                                     @NotBlank String newPassword) {
    @Override public String toString() { return "PatientOtpResetRequest[REDACTED]"; }
}
