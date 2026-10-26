package fu.fa26.sep490.g58.otcms_be.visit.repository;

import fu.fa26.sep490.g58.otcms_be.visit.model.entity.AppointmentArrivalJpaEntity;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentArrivalRepository extends JpaRepository<AppointmentArrivalJpaEntity, UUID> {
    @Modifying
    @Query(value = """
            insert into appointment_arrivals (appointment_id, patient_profile_id, actor_account_id, source,
                reason, arrived_at, recorded_at)
            values (:appointment, :patient, :actor, :source, cast(:reason as varchar), :arrivedAt, clock_timestamp())
            on conflict (appointment_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("appointment") UUID appointmentId, @Param("patient") UUID patientProfileId,
            @Param("actor") UUID actorAccountId, @Param("source") String source, @Param("reason") String reason,
            @Param("arrivedAt") Timestamp arrivedAt);

    @Query(value = """
            select appointment_id, patient_profile_id, actor_account_id, source, reason, arrived_at, recorded_at
            from appointment_arrivals
            where arrived_at >= :from and arrived_at < :to
            order by arrived_at, appointment_id
            """, nativeQuery = true)
    List<Object[]> findArrivals(@Param("from") Timestamp from, @Param("to") Timestamp to, Pageable pageable);
}
