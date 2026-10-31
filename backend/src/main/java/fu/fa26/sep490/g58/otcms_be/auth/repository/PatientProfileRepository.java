package fu.fa26.sep490.g58.otcms_be.auth.repository;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.PatientProfileJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
import java.util.Optional;

public interface PatientProfileRepository extends JpaRepository<PatientProfileJpaEntity, UUID> {
    Page<PatientProfileJpaEntity> findByFullNameContainingIgnoreCase(String fullName, Pageable pageable);
    Page<PatientProfileJpaEntity> findByFullNameContainingIgnoreCaseOrPhoneCanonicalContaining(
            String fullName, String phoneCanonical, Pageable pageable);
    Optional<PatientProfileJpaEntity> findByIdAndAccountIdIsNotNull(UUID id);
    Optional<PatientProfileJpaEntity> findByAccountId(UUID accountId);
}
