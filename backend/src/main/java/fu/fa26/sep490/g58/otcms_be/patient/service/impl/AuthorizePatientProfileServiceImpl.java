package fu.fa26.sep490.g58.otcms_be.patient.service.impl;

import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientIdentityDirectory;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService.AuthorizationScope;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService.AuthorizedPatientProfile;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** @ears REQ-BOOK-005 REQ-BOOK-019 REQ-BOOK-020 REQ-BOOK-021 REQ-BOOK-022 */
@Service
public class AuthorizePatientProfileServiceImpl implements AuthorizePatientProfileService {
    private final PatientIdentityDirectory identities;
    private final PatientProfileDirectory profiles;
    public AuthorizePatientProfileServiceImpl(PatientIdentityDirectory identities, PatientProfileDirectory profiles) {
        this.identities = identities;
        this.profiles = profiles;
    }
    @Override
    @Transactional(readOnly = true)
    public AuthorizedPatientProfile authorize(UUID accountId, UUID targetPatientProfileId) {
        if (accountId == null || targetPatientProfileId == null || !identities.isActivePatient(accountId)) throw new PatientAuthorizationDeniedException();
        UUID ownProfile = profiles.findPatientProfile(accountId).orElseThrow(() -> new PatientAuthorizationDeniedException());
        if (!targetPatientProfileId.equals(ownProfile)) throw new PatientAuthorizationDeniedException();
        return new AuthorizedPatientProfile(ownProfile, AuthorizationScope.SELF);
    }
}
