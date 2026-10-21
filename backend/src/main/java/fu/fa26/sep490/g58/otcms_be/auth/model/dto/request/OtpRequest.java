package fu.fa26.sep490.g58.otcms_be.auth.model.dto.request;

import jakarta.validation.constraints.*;
public class OtpRequest {
    private @NotBlank @Email @Size(max = 254) String email;
    private @NotBlank @Pattern(regexp = "\\d{6}") String otp;
    public OtpRequest() { }
    public OtpRequest(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Pattern(regexp = "\\d{6}") String otp) {
        this.email = email;
        this.otp = otp;
    }
    public String getEmail() { return email; }
    public String email() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getOtp() { return otp; }
    public String otp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }
}
