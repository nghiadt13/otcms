package fu.fa26.sep490.g58.otcms_be.auth.repository;

import fu.fa26.sep490.g58.otcms_be.auth.entity.ConsentEvidence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import fu.fa26.sep490.g58.otcms_be.auth.entity.ConsentActorType;

public interface ConsentEvidenceRepository extends JpaRepository<ConsentEvidence, UUID> {
    Page<ConsentEvidence> findAllByAccountId(UUID accountId, Pageable pageable);
    boolean existsByAccountIdAndActorTypeAndTermsVersionAndPrivacyVersion(UUID accountId, ConsentActorType actorType, String termsVersion, String privacyVersion);
}
