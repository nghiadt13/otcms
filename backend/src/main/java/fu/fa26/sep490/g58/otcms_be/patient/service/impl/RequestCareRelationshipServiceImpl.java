package fu.fa26.sep490.g58.otcms_be.patient.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientIdentityDirectory;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.patient.exception.CareRelationshipException;
import fu.fa26.sep490.g58.otcms_be.patient.mapper.CareRelationshipMapper;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship.State;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship;
import fu.fa26.sep490.g58.otcms_be.patient.repository.CareRelationshipRepository;
import fu.fa26.sep490.g58.otcms_be.patient.service.RequestCareRelationshipService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** @ears REQ-BOOK-005 REQ-BOOK-019 REQ-BOOK-020 REQ-BOOK-021 REQ-BOOK-022 */
@Service
public class RequestCareRelationshipServiceImpl implements RequestCareRelationshipService {
    private final CareRelationshipRepository relationships;
    private final PatientIdentityDirectory identities;
    private final PatientProfileDirectory profiles;
    private final CoordinationGuardService guard;
    private final Clock clock;

    @Autowired
    public RequestCareRelationshipServiceImpl(CareRelationshipRepository relationships,
                                              PatientIdentityDirectory identities, PatientProfileDirectory profiles,
                                              CoordinationGuardService guard) {
        this(relationships, identities, profiles, guard, Clock.systemUTC());
    }

    public RequestCareRelationshipServiceImpl(CareRelationshipRepository relationships,
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
    public CareRelationship request(UUID actorAccountId, UUID dependentPatientProfileId) {
        guard.acquire();
        Instant now = clock.instant();
        requirePatient(actorAccountId);
        if (dependentPatientProfileId == null) {
            throw new CareRelationshipException("VALIDATION_FAILED");
        }
        UUID owner = profiles.findProfileAccount(dependentPatientProfileId)
                .orElseThrow(() -> new CareRelationshipException("NOT_FOUND"));
        if (actorAccountId.equals(owner)) {
            throw new CareRelationshipException("VALIDATION_FAILED");
        }
        if (!identities.isActivePatient(owner)) {
            throw new CareRelationshipException("NOT_FOUND");
        }
        boolean exists = relationships.findFirstByRequesterAccountIdAndDependentPatientProfileIdAndStateIn(
                actorAccountId, dependentPatientProfileId, List.of(State.REQUESTED, State.CONFIRMED)).isPresent();
        if (exists) {
            throw new CareRelationshipException("RELATIONSHIP_STATE_CONFLICT");
        }
        CareRelationship relationship = new CareRelationship(UUID.randomUUID(), actorAccountId,
                dependentPatientProfileId, owner, State.REQUESTED, now, null, null, null);
        return CareRelationshipMapper.toModel(relationships.save(CareRelationshipMapper.toEntity(relationship)));
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
