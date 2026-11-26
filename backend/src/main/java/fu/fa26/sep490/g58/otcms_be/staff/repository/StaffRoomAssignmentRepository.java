package fu.fa26.sep490.g58.otcms_be.staff.repository;

import fu.fa26.sep490.g58.otcms_be.staff.model.entity.DoctorWorkShiftJpaEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffRoomAssignmentRepository extends JpaRepository<DoctorWorkShiftJpaEntity, UUID> {
    record Assignment(UUID roomId, UUID accountId) { }

    @Query("select distinct new fu.fa26.sep490.g58.otcms_be.staff.repository.StaffRoomAssignment(s.roomId, p.accountId) "
            + "from DoctorWorkShiftJpaEntity s join StaffProfileJpaEntity p on p.id=s.doctorProfileId "
            + "join AccountJpaEntity a on a.id=p.accountId where p.status='ACTIVE' and a.accountType <> "
            + "fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType.DOCTOR and s.roomId in :roomIds "
            + "and s.startsAt <= :at and s.endsAt > :at")
    List<StaffRoomAssignment> findCurrentAssignmentRows(@Param("roomIds") Collection<UUID> roomIds, @Param("at") Instant at);

    default List<Assignment> findCurrentAssignments(Collection<UUID> roomIds, Instant at) {
        return findCurrentAssignmentRows(roomIds, at).stream()
                .map(row -> new Assignment(row.roomId(), row.accountId())).toList();
    }

    @Query("select case when count(s) > 0 then true else false end from DoctorWorkShiftJpaEntity s "
            + "join StaffProfileJpaEntity p on p.id=s.doctorProfileId join AccountJpaEntity a on a.id=p.accountId "
            + "where p.accountId=:accountId and p.status='ACTIVE' and a.accountType <> "
            + "fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType.DOCTOR and s.roomId=:roomId "
            + "and s.startsAt <= :at and s.endsAt > :at")
    boolean hasActiveAssignment(@Param("accountId") UUID accountId, @Param("roomId") UUID roomId,
            @Param("at") Instant at);
}
