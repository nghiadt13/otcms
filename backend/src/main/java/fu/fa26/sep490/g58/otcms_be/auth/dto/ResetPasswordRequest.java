package fu.fa26.sep490.g58.otcms_be.auth.dto;
import jakarta.validation.constraints.*;
public record ResetPasswordRequest(@NotBlank String token, @NotBlank @Size(min=12,max=64) String newPassword) {}
