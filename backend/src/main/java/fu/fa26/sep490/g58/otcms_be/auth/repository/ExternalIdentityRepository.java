package fu.fa26.sep490.g58.otcms_be.auth.repository;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.ExternalIdentityJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.IdentityProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface ExternalIdentityRepository extends JpaRepository<ExternalIdentityJpaEntity, UUID> {
    Optional<ExternalIdentityJpaEntity> findByProviderAndProviderSubject(IdentityProvider provider, String providerSubject);
    List<ExternalIdentityJpaEntity> findAllByAccount_Id(UUID accountId);
}
