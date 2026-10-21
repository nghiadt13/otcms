package fu.fa26.sep490.g58.otcms_be.auth.service;

import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.GoogleExchangeRequest;

public interface GoogleAuthenticationService {
    AuthSessionService.IssuedSession exchange(String code, String verifier, String redirectUri,
                                               String clientIp, String userAgent);

    AuthSessionService.IssuedSession exchange(GoogleExchangeRequest request, String clientIp, String userAgent);
}
