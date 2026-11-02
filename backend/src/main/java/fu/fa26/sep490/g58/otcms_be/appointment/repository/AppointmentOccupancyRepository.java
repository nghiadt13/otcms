package fu.fa26.sep490.g58.otcms_be.appointment.repository;

import fu.fa26.sep490.g58.otcms_be.appointment.mapper.AppointmentRowMapper;
import fu.fa26.sep490.g58.otcms_be.appointment.model.entity.AppointmentJpaEntity;
import fu.fa26.sep490.g58.otcms_be.appointment.model.AppointmentOccupancy;
import fu.fa26.sep490.g58.otcms_be.appointment.model.AvailabilityInterval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.query.Param;

public interface AppointmentOccupancyRepository extends JpaRepository<AppointmentJpaEntity, UUID> {
    @Query(value = """
            select a.start_time, a.occupied_until from appointments a
            where a.doctor_profile_id = :doctorId and a.status in ('CONFIRMED', 'CHECKED_IN')
            order by a.start_time, a.id
            """, nativeQuery = true)
    List<Object[]> findProtectedBookingIntervals(@Param("doctorId") UUID doctorId);

    @Query(value = """
            select count(*) from appointments a where a.doctor_profile_id = :doctorId
              and a.status in ('CONFIRMED', 'CHECKED_IN')
              and a.start_time < :toExclusive and a.occupied_until > :fromInclusive
            """, nativeQuery = true)
    long countBookingsOverlappingTimeOff(@Param("doctorId") UUID doctorId,
            @Param("fromInclusive") Instant fromInclusive, @Param("toExclusive") Instant toExclusive);

    @Query(value = """
            select count(*) from appointments a where a.doctor_profile_id = :doctorId
              and a.service_id = :serviceId and a.status in ('CONFIRMED', 'CHECKED_IN')
            """, nativeQuery = true)
    long countBookingsForAssignment(@Param("doctorId") UUID doctorId, @Param("serviceId") UUID serviceId);

    @Query(value = """
            select a.patient_profile_id, a.visit_id, a.doctor_profile_id, a.status,
                   a.start_time, a.end_time, a.occupied_until,
                   (select max(aa.occurred_at) from appointment_audits aa
                    where aa.appointment_id = a.id and aa.action = 'STATUS_CHANGED'
                      and aa.new_status = 'COMPLETED') as completed_at
            from appointments a where a.id = :appointmentId
            """, nativeQuery = true)
    List<Object[]> findVisitLinkEvidence(@Param("appointmentId") UUID appointmentId);

    @Query(value = """
            select id, doctor_profile_id, start_time, occupied_until
            from appointments
            where doctor_profile_id in (:doctorIds)
              and status in ('CONFIRMED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED')
              and start_time < :toExclusive and occupied_until > :fromInclusive
              and (cast(:excludedAppointmentId as uuid) is null or id <> cast(:excludedAppointmentId as uuid))
            order by doctor_profile_id, start_time, id
            """, nativeQuery = true)
    List<Object[]> findOccupancyRows(@Param("doctorIds") Collection<UUID> doctorIds,
            @Param("fromInclusive") Timestamp fromInclusive, @Param("toExclusive") Timestamp toExclusive,
            @Param("excludedAppointmentId") UUID excludedAppointmentId);

    default List<AppointmentOccupancy> findOccupiedIntervals(Collection<UUID> doctorProfileIds,
            Instant fromInclusive, Instant toExclusive, UUID excludedAppointmentId) {
        if (doctorProfileIds == null || doctorProfileIds.isEmpty() || doctorProfileIds.stream().anyMatch(id -> id == null)
                || fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("Doctor IDs and a positive bounded interval are required");
        }
        return findOccupancyRows(doctorProfileIds.stream().distinct().sorted().toList(),
                Timestamp.from(fromInclusive), Timestamp.from(toExclusive), excludedAppointmentId)
                .stream().map(row -> new AppointmentOccupancy(AppointmentRowMapper.uuid(row[0]),
                        AppointmentRowMapper.uuid(row[1]), new AvailabilityInterval(
                        AppointmentRowMapper.instant(row[2]), AppointmentRowMapper.instant(row[3])))).toList();
    }
}
