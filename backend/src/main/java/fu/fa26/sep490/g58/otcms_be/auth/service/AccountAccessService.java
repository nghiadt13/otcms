package fu.fa26.sep490.g58.otcms_be.auth.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;

public interface AccountAccessService {
    AccessState evaluate(AuthenticatedAccount actor);

    enum AccessState {
        ALLOWED, PASSWORD_CHANGE_REQUIRED, INVALID_SESSION
    }
}
