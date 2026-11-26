package fu.fa26.sep490.g58.otcms_be.staff.repository;

import fu.fa26.sep490.g58.otcms_be.staff.model.entity.DoctorWorkShiftJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** JPA persistence queries for staff work shifts managed by the staff scheduling feature. */
public interface StaffShiftAdminRepository extends JpaRepository<DoctorWorkShiftJpaEntity, UUID> {
    interface ShiftRow {
        UUID getId();
        UUID getStaffProfileId();
        String getDisplayName();
        String getAccountType();
        UUID getRoomId();
        String getDisplayLabel();
        String getRoomType();
        Instant getStartsAt();
        Instant getEndsAt();
    }

    interface StaffOptionRow {
        UUID getId();
        String getDisplayName();
        String getAccountType();
    }

    interface StaffAccountRow { UUID getAccountId(); }

    @Query(value = """
            SELECT s.id AS id, s.staff_profile_id AS "staffProfileId", p.display_name AS "displayName",
                   a.account_type AS "accountType", s.room_id AS "roomId", r.display_label AS "displayLabel",
                   r.room_type AS "roomType", s.starts_at AS "startsAt", s.ends_at AS "endsAt"
            FROM work_shifts s
            JOIN staff_profiles p ON p.id=s.staff_profile_id
            JOIN accounts a ON a.id=p.account_id
            JOIN rooms r ON r.id=s.room_id
            WHERE s.starts_at < :until AND s.ends_at > :from AND p.status='ACTIVE'
              AND a.status='ACTIVE' AND a.account_type IN ('RECEPTIONIST','LAB_TECHNICIAN','IMAGING_TECHNICIAN')
              AND (:staffId IS NULL OR s.staff_profile_id=:staffId)
              AND (:role IS NULL OR a.account_type=:role)
              AND (:roomId IS NULL OR s.room_id=:roomId)
            ORDER BY s.starts_at, p.display_name
            """, nativeQuery = true)
    List<ShiftRow> findShifts(@Param("from") Instant from, @Param("until") Instant until,
            @Param("staffId") UUID staffId, @Param("role") String role, @Param("roomId") UUID roomId);

    @Query(value = """
            SELECT p.id AS id, p.display_name AS "displayName", a.account_type AS "accountType"
            FROM staff_profiles p JOIN accounts a ON a.id=p.account_id
            WHERE p.account_id=:accountId AND p.status='ACTIVE' AND a.status='ACTIVE'
            """, nativeQuery = true)
    Optional<StaffOptionRow> findActiveStaffOption(@Param("accountId") UUID accountId);

    @Query(value = """
            SELECT p.account_id AS "accountId" FROM staff_profiles p
            WHERE p.id=:staffId AND p.status='ACTIVE'
            """, nativeQuery = true)
    Optional<StaffAccountRow> findActiveStaffAccount(@Param("staffId") UUID staffId);

    @Query(value = """
            SELECT count(*) > 0 FROM work_shifts
            WHERE (staff_profile_id=:staffId OR room_id=:roomId)
              AND starts_at < :endsAt AND ends_at > :startsAt
              AND (:ignoreId IS NULL OR id<>:ignoreId)
            """, nativeQuery = true)
    boolean existsOverlappingShift(@Param("staffId") UUID staffId, @Param("roomId") UUID roomId,
            @Param("startsAt") Instant startsAt, @Param("endsAt") Instant endsAt,
            @Param("ignoreId") UUID ignoreId);

    @Query(value = """
            SELECT s.id AS id, s.staff_profile_id AS "staffProfileId", p.display_name AS "displayName",
                   a.account_type AS "accountType", s.room_id AS "roomId", r.display_label AS "displayLabel",
                   r.room_type AS "roomType", s.starts_at AS "startsAt", s.ends_at AS "endsAt"
            FROM work_shifts s JOIN staff_profiles p ON p.id=s.staff_profile_id
            JOIN accounts a ON a.id=p.account_id JOIN rooms r ON r.id=s.room_id
            WHERE s.id=:id AND a.account_type <> 'DOCTOR'
            """, nativeQuery = true)
    Optional<ShiftRow> findShiftRow(@Param("id") UUID id);

    @Query(value = """
            SELECT s FROM DoctorWorkShiftJpaEntity s JOIN StaffProfileJpaEntity p ON p.id=s.doctorProfileId
            JOIN AccountJpaEntity a ON a.id=p.accountId
            WHERE s.id=:id AND a.accountType <> fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType.DOCTOR
            """)
    Optional<DoctorWorkShiftJpaEntity> findNonDoctorShift(@Param("id") UUID id);

}
