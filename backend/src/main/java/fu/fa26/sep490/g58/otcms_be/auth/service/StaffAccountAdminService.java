package fu.fa26.sep490.g58.otcms_be.auth.service;

import fu.fa26.sep490.g58.otcms_be.auth.model.dto.StaffAccountDtos;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.util.UUID;

public interface StaffAccountAdminService {
    StaffAccountDtos.Created create(AuthenticatedAccount actor, String role, String username,
            String initialPassword, String correlationId);
    StaffAccountDtos.Page list(AuthenticatedAccount actor, String query, String role, String status, int page, int size);
    StaffAccountDtos.Item detail(AuthenticatedAccount actor, UUID accountId);
    StaffAccountDtos.Item activate(AuthenticatedAccount actor, UUID accountId, String correlationId);
    StaffAccountDtos.Item deactivate(AuthenticatedAccount actor, UUID accountId, String correlationId);
    StaffAccountDtos.Reissued reissuePassword(AuthenticatedAccount actor, UUID accountId, String correlationId);
}
