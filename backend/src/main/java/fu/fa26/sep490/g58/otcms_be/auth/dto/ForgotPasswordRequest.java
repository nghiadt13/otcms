package fu.fa26.sep490.g58.otcms_be.auth.dto;
import jakarta.validation.constraints.NotBlank;
public record ForgotPasswordRequest(@NotBlank String email) {}
