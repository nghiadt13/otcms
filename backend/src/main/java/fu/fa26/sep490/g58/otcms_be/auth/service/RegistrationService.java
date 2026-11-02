package fu.fa26.sep490.g58.otcms_be.auth.service;

import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.RegistrationRequest;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.RegistrationResponse;

public interface RegistrationService {
    RegistrationResponse register(RegistrationRequest request, String clientIp);
    void verifyEmail(String email, String otp);
    void resendOtp(String email);
}
