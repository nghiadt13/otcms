package fu.fa26.sep490.g58.otcms_be.servicecatalog.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.util.UUID;

public interface DeactivateClinicServiceService {
    void deactivate(UUID serviceId, AuthenticatedAccount actor, String correlationId);
}
