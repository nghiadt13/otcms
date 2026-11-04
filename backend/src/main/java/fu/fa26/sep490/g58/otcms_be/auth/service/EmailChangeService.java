package fu.fa26.sep490.g58.otcms_be.auth.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;

public interface EmailChangeService {
    void request(AuthenticatedAccount actor, String newEmail);
    void verify(AuthenticatedAccount actor, String code);
}
