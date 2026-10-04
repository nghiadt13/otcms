package fu.fa26.sep490.g58.otcms_be.auth.service;

import fu.fa26.sep490.g58.otcms_be.auth.dto.RegistrationRequest;
import fu.fa26.sep490.g58.otcms_be.auth.dto.RegistrationResponse;

public interface RegistrationService {
    RegistrationResponse register(RegistrationRequest request, String clientIp);
    void verifyEmail(String email, String otp);
    void resendOtp(String email);
}
