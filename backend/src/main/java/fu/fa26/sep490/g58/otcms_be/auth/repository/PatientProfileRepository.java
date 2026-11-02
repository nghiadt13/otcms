package fu.fa26.sep490.g58.otcms_be.auth.repository;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.PatientProfileJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;
import java.util.Optional;
import java.util.List;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientProfileRepository extends JpaRepository<PatientProfileJpaEntity, UUID> {
    List<PatientProfileJpaEntity> findByPhoneCanonicalAndDateOfBirth(String phoneCanonical, LocalDate dateOfBirth,
            Pageable pageable);

    // Normalize legacy stored names too; parameters are bound, never interpolated.
    @Query(value = """
            SELECT * FROM patient_profiles
            WHERE phone_canonical = :phone AND date_of_birth = :dob
              AND lower(normalize(btrim(regexp_replace(full_name, '\\s+', ' ', 'g')), NFC))
                  = lower(normalize(:fullName, NFC))
            """, nativeQuery = true)
    List<PatientProfileJpaEntity> findExactMatches(@Param("phone") String phone, @Param("dob") LocalDate dob,
            @Param("fullName") String fullName, Pageable pageable);

    Page<PatientProfileJpaEntity> findByFullNameContainingIgnoreCase(String fullName, Pageable pageable);
    Page<PatientProfileJpaEntity> findByFullNameContainingIgnoreCaseOrPhoneCanonicalContaining(
            String fullName, String phoneCanonical, Pageable pageable);
    Optional<PatientProfileJpaEntity> findByIdAndAccountIdIsNotNull(UUID id);
    Optional<PatientProfileJpaEntity> findByAccountId(UUID accountId);
}
