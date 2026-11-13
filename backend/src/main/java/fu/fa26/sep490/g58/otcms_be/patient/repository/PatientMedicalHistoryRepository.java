package fu.fa26.sep490.g58.otcms_be.patient.repository;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.PatientProfileJpaEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PatientMedicalHistoryRepository extends JpaRepository<PatientProfileJpaEntity, UUID> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE patient_profiles SET conditions=:conditions, allergies=:allergies, procedures=:procedures,
                injuries=:injuries, orthopedic_history=:orthopedic, medical_history_updated_at=:now,
                medical_history_updated_by=:actor, medical_history_version=1
            WHERE id=:profile AND medical_history_updated_at IS NULL
            """, nativeQuery = true)
    int insertInitial(@Param("profile") UUID profile, @Param("conditions") String conditions,
            @Param("allergies") String allergies, @Param("procedures") String procedures,
            @Param("injuries") String injuries, @Param("orthopedic") String orthopedic,
            @Param("now") Instant now, @Param("actor") UUID actor);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE patient_profiles
            SET conditions=:conditions, allergies=:allergies, procedures=:procedures,
                injuries=:injuries, orthopedic_history=:orthopedic,
                medical_history_updated_at=:now, medical_history_updated_by=:actor, medical_history_version=medical_history_version+1
            WHERE id=:profile AND medical_history_version=:expected
            """, nativeQuery = true)
    int updateIfVersion(@Param("profile") UUID profile, @Param("conditions") String conditions,
            @Param("allergies") String allergies, @Param("procedures") String procedures,
            @Param("injuries") String injuries, @Param("orthopedic") String orthopedic,
            @Param("now") Instant now, @Param("actor") UUID actor, @Param("expected") long expected);
}
