package fu.fa26.sep490.g58.otcms_be.patient.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientIdentityDirectory;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.patient.exception.CareRelationshipException;
import fu.fa26.sep490.g58.otcms_be.patient.mapper.CareRelationshipMapper;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship.State;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship;
import fu.fa26.sep490.g58.otcms_be.patient.repository.CareRelationshipRepository;
import fu.fa26.sep490.g58.otcms_be.patient.service.ConfirmCareRelationshipService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** @ears REQ-BOOK-005 REQ-BOOK-019 REQ-BOOK-020 REQ-BOOK-021 REQ-BOOK-022 */
@Service
public class ConfirmCareRelationshipServiceImpl implements ConfirmCareRelationshipService {
    private final CareRelationshipRepository relationships;
    private final PatientIdentityDirectory identities;
    private final PatientProfileDirectory profiles;
    private final CoordinationGuardService guard;
    private final Clock clock;

    @Autowired
    public ConfirmCareRelationshipServiceImpl(CareRelationshipRepository relationships,
                                              PatientIdentityDirectory identities, PatientProfileDirectory profiles,
                                              CoordinationGuardService guard) {
        this(relationships, identities, profiles, guard, Clock.systemUTC());
    }

    public ConfirmCareRelationshipServiceImpl(CareRelationshipRepository relationships,
                                              PatientIdentityDirectory identities, PatientProfileDirectory profiles,
                                              CoordinationGuardService guard, Clock clock) {
        this.relationships = relationships;
        this.identities = identities;
        this.profiles = profiles;
        this.guard = guard;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CareRelationship confirm(UUID actorAccountId, UUID relationshipId) {
        guard.acquire();
        Instant now = clock.instant();
        requirePatient(actorAccountId);
        CareRelationship relation = relationships.findById(relationshipId).map(CareRelationshipMapper::toModel)
                .orElseThrow(() -> new CareRelationshipException("NOT_FOUND"));
        UUID owner = profiles.findProfileAccount(relation.dependentPatientProfileId())
                .orElseThrow(() -> new CareRelationshipException("NOT_FOUND"));
        if (!actorAccountId.equals(owner) || !actorAccountId.equals(relation.confirmingAccountId())) {
            throw new CareRelationshipException("NOT_FOUND");
        }
        if (!identities.isActivePatient(relation.requesterAccountId())
                || profiles.findPatientProfile(relation.requesterAccountId()).isEmpty()) {
            throw new CareRelationshipException("NOT_FOUND");
        }
        if (relation.state() != State.REQUESTED) {
            throw new CareRelationshipException("RELATIONSHIP_STATE_CONFLICT");
        }
        return CareRelationshipMapper.toModel(relationships.save(
                CareRelationshipMapper.toEntity(relation.confirm(now))));
    }

    private void requirePatient(UUID actor) {
        if (actor == null || !identities.isActivePatient(actor)) {
            throw new CareRelationshipException("FORBIDDEN");
        }
        if (profiles.findPatientProfile(actor).isEmpty()) {
            throw new CareRelationshipException("NOT_FOUND");
        }
    }
}
