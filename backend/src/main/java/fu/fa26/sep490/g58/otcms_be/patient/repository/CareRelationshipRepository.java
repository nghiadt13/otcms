package fu.fa26.sep490.g58.otcms_be.patient.repository;

import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship.State;
import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship;
import fu.fa26.sep490.g58.otcms_be.patient.model.entity.CareRelationshipJpaEntity;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CareRelationshipRepository extends JpaRepository<CareRelationshipJpaEntity, UUID> {
    Optional<CareRelationshipJpaEntity> findFirstByRequesterAccountIdAndDependentPatientProfileIdAndStateIn(
            UUID requesterAccountId, UUID dependentPatientProfileId, Collection<CareRelationship.State> states);
}
