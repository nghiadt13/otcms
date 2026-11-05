package fu.fa26.sep490.g58.otcms_be.auth.service;

import fu.fa26.sep490.g58.otcms_be.auth.model.dto.MyAccountDtos.Avatar;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.util.UUID;

public interface MyAccountService {
    MyAccountQueryService.MyAccountView get(AuthenticatedAccount actor);
    MyAccountQueryService.MyAccountView updateDisplayName(AuthenticatedAccount actor, String name);
    MyAccountQueryService.MyAccountView updateAvatar(AuthenticatedAccount actor, byte[] content, String contentType);
    void removeAvatar(AuthenticatedAccount actor);
    Avatar avatar(AuthenticatedAccount actor, UUID accountId);
}
