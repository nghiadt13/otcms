package fu.fa26.sep490.g58.otcms_be.patient.repository;

import fu.fa26.sep490.g58.otcms_be.patient.model.entity.PatientMedicalHistoryJpaEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PatientMedicalHistoryRepository extends JpaRepository<PatientMedicalHistoryJpaEntity, UUID> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO patient_medical_histories
                (patient_profile_id, conditions, allergies, procedures, injuries, orthopedic_history,
                 updated_at, updated_by_account_id, version)
            VALUES (:profile, :conditions, :allergies, :procedures, :injuries, :orthopedic, :now, :actor, 1)
            ON CONFLICT (patient_profile_id) DO NOTHING
            """, nativeQuery = true)
    int insertInitial(@Param("profile") UUID profile, @Param("conditions") String conditions,
            @Param("allergies") String allergies, @Param("procedures") String procedures,
            @Param("injuries") String injuries, @Param("orthopedic") String orthopedic,
            @Param("now") Instant now, @Param("actor") UUID actor);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE patient_medical_histories
            SET conditions=:conditions, allergies=:allergies, procedures=:procedures,
                injuries=:injuries, orthopedic_history=:orthopedic,
                updated_at=:now, updated_by_account_id=:actor, version=version+1
            WHERE patient_profile_id=:profile AND version=:expected
            """, nativeQuery = true)
    int updateIfVersion(@Param("profile") UUID profile, @Param("conditions") String conditions,
            @Param("allergies") String allergies, @Param("procedures") String procedures,
            @Param("injuries") String injuries, @Param("orthopedic") String orthopedic,
            @Param("now") Instant now, @Param("actor") UUID actor, @Param("expected") long expected);
}
