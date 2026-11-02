package fu.fa26.sep490.g58.otcms_be.patient.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientIdentityDirectory;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship.State;
import fu.fa26.sep490.g58.otcms_be.patient.repository.CareRelationshipRepository;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientBillingProfileService;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizePatientBillingProfileServiceImpl implements AuthorizePatientBillingProfileService {
    private final PatientIdentityDirectory identities;
    private final PatientProfileDirectory profiles;
    private final CareRelationshipRepository relationships;

    public AuthorizePatientBillingProfileServiceImpl(PatientIdentityDirectory identities,
            PatientProfileDirectory profiles, CareRelationshipRepository relationships) {
        this.identities = identities;
        this.profiles = profiles;
        this.relationships = relationships;
    }

    @Override
    @Transactional(readOnly = true)
    public void authorize(UUID accountId, UUID patientProfileId) {
        if (patientProfileId == null || !accessibleProfileIds(accountId).contains(patientProfileId)) {
            throw new PatientAuthorizationDeniedException();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> accessibleProfileIds(UUID accountId) {
        if (accountId == null || !identities.isActivePatient(accountId)) {
            throw new PatientAuthorizationDeniedException();
        }
        UUID ownProfile = profiles.findPatientProfile(accountId)
                .orElseThrow(PatientAuthorizationDeniedException::new);
        List<UUID> accessible = new ArrayList<>();
        accessible.add(ownProfile);
        relationships.findAllByRequesterAccountIdAndState(accountId, State.CONFIRMED).stream()
                .map(relationship -> relationship.getDependentPatientProfileId())
                .forEach(accessible::add);
        return List.copyOf(accessible);
    }
}
