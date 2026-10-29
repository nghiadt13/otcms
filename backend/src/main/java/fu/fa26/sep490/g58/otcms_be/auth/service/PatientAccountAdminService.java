package fu.fa26.sep490.g58.otcms_be.auth.service;

import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.PatientAccountAdminResponse;
import fu.fa26.sep490.g58.otcms_be.auth.model.dto.response.PatientAccountsPageResponse;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.util.UUID;

public interface PatientAccountAdminService {
    PatientAccountsPageResponse list(AuthenticatedAccount actor, String query, String status, int page, int size);
    PatientAccountAdminResponse detail(AuthenticatedAccount actor, UUID accountId);
    PatientAccountAdminResponse activate(AuthenticatedAccount actor, UUID accountId);
    PatientAccountAdminResponse deactivate(AuthenticatedAccount actor, UUID accountId);
}
