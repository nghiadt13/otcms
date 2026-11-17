package fu.fa26.sep490.g58.otcms_be.staff.repository;

import fu.fa26.sep490.g58.otcms_be.staff.model.entity.DoctorWorkShiftJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoctorWorkShiftRepository extends JpaRepository<DoctorWorkShiftJpaEntity, UUID> {
    @Query("select case when count(s) > 0 then true else false end from DoctorWorkShiftJpaEntity s "
            + "join StaffProfileJpaEntity p on p.id = s.doctorProfileId "
            + "where p.accountId = :accountId and p.status = 'ACTIVE' and s.roomId is not null "
            + "and s.startsAt <= :at and s.endsAt > :at")
    boolean hasActiveRoomShift(@Param("accountId") UUID accountId, @Param("at") Instant at);
    @Query("select case when count(s) > 0 then true else false end from DoctorWorkShiftJpaEntity s "
            + "join StaffProfileJpaEntity p on p.id = s.doctorProfileId "
            + "where p.accountId = :accountId and p.status = 'ACTIVE' and s.roomId = :roomId "
            + "and s.startsAt <= :at and s.endsAt > :at")
    boolean hasActiveRoomAssignment(@Param("accountId") UUID accountId, @Param("roomId") UUID roomId,
                                    @Param("at") Instant at);

    List<DoctorWorkShiftJpaEntity> findAllByDoctorProfileIdOrderByStartsAtAscIdAsc(UUID doctorProfileId);
    java.util.Optional<DoctorWorkShiftJpaEntity> findByIdAndDoctorProfileId(UUID id, UUID doctorProfileId);
    boolean existsByDoctorProfileIdAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(
            UUID doctorProfileId, Instant startsAt, Instant endsAt);

    @Query("select s from DoctorWorkShiftJpaEntity s where s.doctorProfileId = :doctorId "
            + "and s.startsAt < :rangeEnd and s.endsAt > :rangeStart order by s.startsAt, s.id")
    List<DoctorWorkShiftJpaEntity> findInRange(@Param("doctorId") UUID doctorId,
                                               @Param("rangeStart") Instant rangeStart,
                                               @Param("rangeEnd") Instant rangeEnd);

    @Query("select s from DoctorWorkShiftJpaEntity s where s.doctorProfileId in :doctorIds "
            + "and s.startsAt < :rangeEnd and s.endsAt > :rangeStart "
            + "order by s.doctorProfileId, s.startsAt, s.id")
    List<DoctorWorkShiftJpaEntity> findOverlapping(@Param("doctorIds") List<UUID> doctorIds,
                                                   @Param("rangeStart") Instant rangeStart,
                                                   @Param("rangeEnd") Instant rangeEnd);
}
