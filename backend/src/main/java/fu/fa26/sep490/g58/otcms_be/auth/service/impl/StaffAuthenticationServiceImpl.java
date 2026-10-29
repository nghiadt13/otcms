package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.exception.CredentialLoginException;
import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.auth.service.CredentialLoginService;
import fu.fa26.sep490.g58.otcms_be.auth.service.StaffAuthenticationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffAuthenticationServiceImpl implements StaffAuthenticationService {
    private final CredentialLoginService credentials;
    private final AuthSessionService sessions;

    public StaffAuthenticationServiceImpl(CredentialLoginService credentials, AuthSessionService sessions) {
        this.credentials = credentials;
        this.sessions = sessions;
    }

    @Override
    @Transactional(noRollbackFor = CredentialLoginException.class)
    public AuthSessionService.IssuedSession login(String identifier, String password, String ip,
                                                 String userAgent, boolean administratorUsername) {
        // Keep the credential row lock until the new session has been persisted.
        var authenticated = administratorUsername
                ? credentials.loginAdministrator(identifier, password, ip)
                : credentials.loginStaff(identifier, password, ip);
        return sessions.create(authenticated.accountId(), ip, userAgent);
    }
}
