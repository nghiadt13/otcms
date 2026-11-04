package fu.fa26.sep490.g58.otcms_be.patient.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.patient.service.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizePatientProfileServiceImpl implements AuthorizePatientProfileService {
    private final PatientProfileAccessService access;
    private final PatientProfileDirectory profiles;
    public AuthorizePatientProfileServiceImpl(PatientProfileAccessService access, PatientProfileDirectory profiles) {
        this.access = access; this.profiles = profiles;
    }
    @Override
    @Transactional(readOnly = true)
    public AuthorizedPatientProfile authorize(UUID accountId, UUID targetPatientProfileId) {
        if (!access.canAccess(accountId, targetPatientProfileId)) throw new PatientAuthorizationDeniedException();
        boolean self = profiles.findPatientProfile(accountId).filter(targetPatientProfileId::equals).isPresent();
        return new AuthorizedPatientProfile(targetPatientProfileId, self ? AuthorizationScope.SELF : AuthorizationScope.DEPENDENT);
    }
}
