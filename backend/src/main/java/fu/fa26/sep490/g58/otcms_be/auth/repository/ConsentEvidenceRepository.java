package fu.fa26.sep490.g58.otcms_be.auth.repository;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.ConsentEvidenceJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.ConsentActorType;

public interface ConsentEvidenceRepository extends JpaRepository<ConsentEvidenceJpaEntity, UUID> {
    Page<ConsentEvidenceJpaEntity> findAllByAccountId(UUID accountId, Pageable pageable);
    boolean existsByAccountIdAndActorTypeAndTermsVersionAndPrivacyVersion(UUID accountId, ConsentActorType actorType, String termsVersion, String privacyVersion);
}
