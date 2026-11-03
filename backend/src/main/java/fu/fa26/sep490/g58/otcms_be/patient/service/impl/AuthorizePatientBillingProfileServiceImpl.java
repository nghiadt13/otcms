package fu.fa26.sep490.g58.otcms_be.patient.service.impl;

import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.patient.service.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthorizePatientBillingProfileServiceImpl implements AuthorizePatientBillingProfileService {
    private final PatientProfileAccessService access;
    public AuthorizePatientBillingProfileServiceImpl(PatientProfileAccessService access) { this.access = access; }
    public void authorize(UUID accountId, UUID profileId) {
        if (!access.canAccess(accountId, profileId)) throw new PatientAuthorizationDeniedException();
    }
    public List<UUID> accessibleProfileIds(UUID accountId) {
        var ids = access.accessibleProfileIds(accountId);
        if (ids.isEmpty()) throw new PatientAuthorizationDeniedException();
        return List.copyOf(ids);
    }
}
