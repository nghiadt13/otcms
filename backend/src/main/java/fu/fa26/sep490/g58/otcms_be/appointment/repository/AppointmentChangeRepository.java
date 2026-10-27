package fu.fa26.sep490.g58.otcms_be.appointment.repository;

import fu.fa26.sep490.g58.otcms_be.appointment.mapper.AppointmentRowMapper;
import fu.fa26.sep490.g58.otcms_be.appointment.model.entity.AppointmentJpaEntity;
import fu.fa26.sep490.g58.otcms_be.appointment.model.AppointmentChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentChangeRepository extends JpaRepository<AppointmentJpaEntity, UUID> {
    @Query(value = """
            select id, patient_profile_id, service_id, doctor_profile_id,
                   status, start_time, duration_minutes, buffer_minutes
            from appointments where id = :id
            """, nativeQuery = true)
    List<Object[]> findChangeRows(@Param("id") UUID id);

    default Optional<AppointmentChange> findChangeContext(UUID appointmentId) {
        return findChangeRows(appointmentId).stream().findFirst().map(row -> new AppointmentChange(
                AppointmentRowMapper.uuid(row[0]), AppointmentRowMapper.uuid(row[1]),
                AppointmentRowMapper.uuid(row[2]), AppointmentRowMapper.uuid(row[3]),
                (String) row[4], AppointmentRowMapper.instant(row[5]),
                AppointmentRowMapper.integer(row[6]), AppointmentRowMapper.integer(row[7])));
    }
}
