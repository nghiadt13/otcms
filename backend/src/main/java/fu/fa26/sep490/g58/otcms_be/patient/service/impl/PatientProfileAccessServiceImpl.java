package fu.fa26.sep490.g58.otcms_be.patient.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientIdentityDirectory;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship.State;
import fu.fa26.sep490.g58.otcms_be.patient.repository.CareRelationshipRepository;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientProfileAccessService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PatientProfileAccessServiceImpl implements PatientProfileAccessService {
    private final PatientIdentityDirectory identities;
    private final PatientProfileDirectory profiles;
    private final CareRelationshipRepository links;

    public PatientProfileAccessServiceImpl(PatientIdentityDirectory identities, PatientProfileDirectory profiles,
            CareRelationshipRepository links) {
        this.identities = identities; this.profiles = profiles; this.links = links;
    }

    public Set<UUID> accessibleProfileIds(UUID accountId) {
        if (accountId == null || !identities.isActivePatient(accountId)) return Set.of();
        var own = profiles.findPatientProfile(accountId);
        if (own.isEmpty()) return Set.of();
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(own.get());
        links.findAllByRequesterAccountIdAndState(accountId, State.CONFIRMED)
                .forEach(link -> ids.add(link.getDependentPatientProfileId()));
        return Collections.unmodifiableSet(ids);
    }

    public boolean canAccess(UUID accountId, UUID profileId) {
        return profileId != null && accessibleProfileIds(accountId).contains(profileId);
    }
}
