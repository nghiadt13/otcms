package fu.fa26.sep490.g58.otcms_be.visit.repository;

import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitInitialReceptionJpaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.sql.Timestamp;
import java.util.List;

public interface VisitInitialReceptionJpaRepository extends JpaRepository<VisitInitialReceptionJpaEntity, UUID> {
    @Query(value = """
            select appointment_id, patient_profile_id, arrived_at, receptionist_account_id
            from recorded_appointment_arrivals
            where appointment_id = :appointmentId and patient_profile_id = :patientProfileId
              and arrived_at >= :windowStart and arrived_at <= :windowEnd
            """, nativeQuery = true)
    List<Object[]> findRecordedArrivalEvidence(@Param("appointmentId") UUID appointmentId,
            @Param("patientProfileId") UUID patientProfileId, @Param("windowStart") Timestamp windowStart,
            @Param("windowEnd") Timestamp windowEnd);
}
