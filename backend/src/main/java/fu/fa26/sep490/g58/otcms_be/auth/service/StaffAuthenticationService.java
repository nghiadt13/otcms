package fu.fa26.sep490.g58.otcms_be.auth.service;

public interface StaffAuthenticationService {
    AuthSessionService.IssuedSession login(String identifier, String password, String ip,
                                          String userAgent, boolean administratorUsername);
}
