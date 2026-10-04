package fu.fa26.sep490.g58.otcms_be.auth.dto;
import jakarta.validation.constraints.*;
public record OtpRequest(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Pattern(regexp = "\\d{6}") String otp) { }
