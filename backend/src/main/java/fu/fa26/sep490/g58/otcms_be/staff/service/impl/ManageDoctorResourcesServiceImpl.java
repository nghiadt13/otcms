package fu.fa26.sep490.g58.otcms_be.staff.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentMutationImpactService;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.model.AdminMutationImpact;
import fu.fa26.sep490.g58.otcms_be.common.exception.ResourceMutationBlockedException;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitMutationImpactService;
import fu.fa26.sep490.g58.otcms_be.visit.service.ManageVisitRoomService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.staff.exception.DoctorProfileException;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.DoctorIntervalResponse;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorProfile;
import fu.fa26.sep490.g58.otcms_be.staff.mapper.DoctorProfileMapper;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.DoctorWorkShiftJpaEntity;
import fu.fa26.sep490.g58.otcms_be.staff.repository.StaffProfileRepository;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorWorkShiftRepository;
import fu.fa26.sep490.g58.otcms_be.staff.repository.StaffResourceManagementAuditRepository;
import fu.fa26.sep490.g58.otcms_be.staff.service.ManageDoctorResourcesService;
import java.time.Clock;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManageDoctorResourcesServiceImpl implements ManageDoctorResourcesService {
    private final StaffProfileRepository profiles;
    private final DoctorWorkShiftRepository shifts;
    private final StaffResourceManagementAuditRepository audits;
    private final AuditLogWriter auditLogs;
    private final AppointmentMutationImpactService appointments;
    private final VisitMutationImpactService visits;
    private final CoordinationGuardService guard;
    private final Clock clock;
    private final ManageVisitRoomService rooms;
    private final JdbcTemplate jdbc;

    @Autowired
    public ManageDoctorResourcesServiceImpl(StaffProfileRepository profiles, DoctorWorkShiftRepository shifts,
            StaffResourceManagementAuditRepository audits, AuditLogWriter auditLogs,
            AppointmentMutationImpactService appointments, VisitMutationImpactService visits,
            CoordinationGuardService guard, Clock clock, ManageVisitRoomService rooms, JdbcTemplate jdbc) {
        this.profiles=profiles; this.shifts=shifts; this.audits=audits; this.auditLogs=auditLogs;
        this.appointments=appointments; this.visits=visits; this.guard=guard; this.clock=clock; this.rooms=rooms; this.jdbc=jdbc;
    }

    public ManageDoctorResourcesServiceImpl(StaffProfileRepository profiles, DoctorWorkShiftRepository shifts,
            StaffResourceManagementAuditRepository audits, AuditLogWriter auditLogs,
            AppointmentMutationImpactService appointments, VisitMutationImpactService visits,
            CoordinationGuardService guard, Clock clock) {
        this(profiles, shifts, audits, auditLogs, appointments, visits, guard, clock, null, null);
    }

    public DoctorIntervalResponse createShift(UUID doctorId, Instant startsAt, Instant endsAt,
            AuthenticatedAccount actor, String correlationId) {
        return createShift(doctorId, startsAt, endsAt, null, actor, correlationId);
    }

    public DoctorIntervalResponse updateShift(UUID doctorId, UUID shiftId, Instant startsAt, Instant endsAt,
            AuthenticatedAccount actor, String correlationId) {
        return updateShift(doctorId, shiftId, startsAt, endsAt, null, actor, correlationId);
    }

    @Override @Transactional
    public DoctorIntervalResponse createShift(UUID doctorId, Instant startsAt, Instant endsAt, UUID roomId, AuthenticatedAccount actor, String correlationId) {
        validate(doctorId, startsAt, endsAt, actor, correlationId);
        validateDoctorRoom(roomId);
        guard.acquire(); requireDoctor(doctorId);
        ensureRoomAvailable(roomId, startsAt, endsAt, null);
        var existing = shifts.findAllByDoctorProfileIdOrderByStartsAtAscIdAsc(doctorId);
        if (existing.stream().anyMatch(s -> overlaps(startsAt, endsAt, s.startsAt(), s.endsAt())))
            throw new DoctorProfileException("DOCTOR_SHIFT_OVERLAP");
        UUID id = UUID.randomUUID();
        var saved = shifts.save(new DoctorWorkShiftJpaEntity(id, doctorId, startsAt, endsAt, roomId));
        audit(doctorId, actor.accountId(), "WORK_SHIFT", id, "CREATED", null, intervalJson(startsAt, endsAt), correlationId);
        return new DoctorIntervalResponse(saved.id(), saved.doctorProfileId(), saved.startsAt(), saved.endsAt(), saved.roomId());
    }

    @Override @Transactional
    public void deleteShift(UUID doctorId, UUID shiftId, AuthenticatedAccount actor, String correlationId) {
        validateIds(doctorId, actor, correlationId); if (shiftId == null) throw invalid();
        guard.acquire(); requireDoctor(doctorId);
        var shift = shifts.findByIdAndDoctorProfileId(shiftId, doctorId).orElseThrow(() -> new DoctorProfileException("DOCTOR_SHIFT_NOT_FOUND"));
        requireFuture(shift.startsAt());
        var other = shifts.findAllByDoctorProfileIdOrderByStartsAtAscIdAsc(doctorId).stream().filter(s -> !s.id().equals(shiftId)).toList();
        AdminMutationImpact impact = shiftImpact(doctorId, shiftId, null, null, other);
        if (impact.isBlocked()) throw new ResourceMutationBlockedException(impact);
        shifts.delete(shift);
        audit(doctorId, actor.accountId(), "WORK_SHIFT", shiftId, "DELETED", intervalJson(shift.startsAt(), shift.endsAt()), "{}", correlationId);
    }

    @Override @Transactional
    public DoctorIntervalResponse updateShift(UUID doctorId, UUID shiftId, Instant startsAt, Instant endsAt, UUID roomId,
            AuthenticatedAccount actor, String correlationId) {
        validate(doctorId, startsAt, endsAt, actor, correlationId); if (shiftId == null) throw invalid();
        validateDoctorRoom(roomId);
        guard.acquire(); requireDoctor(doctorId);
        var shift=shifts.findByIdAndDoctorProfileId(shiftId, doctorId).orElseThrow(() -> new DoctorProfileException("DOCTOR_SHIFT_NOT_FOUND"));
        requireFuture(shift.startsAt());
        ensureRoomAvailable(roomId, startsAt, endsAt, shiftId);
        var other=shifts.findAllByDoctorProfileIdOrderByStartsAtAscIdAsc(doctorId).stream().filter(s -> !s.id().equals(shiftId)).toList();
        if (other.stream().anyMatch(s -> overlaps(startsAt, endsAt, s.startsAt(), s.endsAt())))
            throw new DoctorProfileException("DOCTOR_SHIFT_OVERLAP");
        AdminMutationImpact impact = shiftImpact(doctorId, shiftId, startsAt, endsAt, other);
        if (impact.isBlocked()) throw new ResourceMutationBlockedException(impact);
        String before=intervalJson(shift.startsAt(), shift.endsAt()); shift.updateInterval(startsAt, endsAt, roomId);
        var saved=shifts.save(shift);
        audit(doctorId, actor.accountId(), "WORK_SHIFT", shiftId, "UPDATED", before, intervalJson(startsAt, endsAt), correlationId);
        return new DoctorIntervalResponse(saved.id(), saved.doctorProfileId(), saved.startsAt(), saved.endsAt(), saved.roomId());
    }


    @Override @Transactional
    public DoctorProfile updateDirectoryState(UUID doctorId, boolean publiclyVisible, boolean acceptingNewBookings,
            boolean inActiveDirectory, AuthenticatedAccount actor, String correlationId) {
        validateIds(doctorId, actor, correlationId); guard.acquire();
        var profile = profiles.findByIdAndDoctorTrue(doctorId)
                .orElseThrow(() -> new DoctorProfileException("DOCTOR_PROFILE_NOT_FOUND"));
        var before = directoryState(profile.publiclyVisible(), profile.acceptingNewBookings(), profile.inActiveDirectory());
        profile.updateDirectoryState(publiclyVisible, acceptingNewBookings, inActiveDirectory);
        var after = directoryState(publiclyVisible, acceptingNewBookings, inActiveDirectory);
        // before/after are Maps so audit_logs stores jsonb objects, as the old staff_resource_audits rows did.
        // The correlation id comes from the request MDC (CorrelationIdFilter), which AuditLogWriter records.
        auditLogs.record(AuditEntry.builder("staff_resource_audits", "STAFF_PROFILE", "UPDATE_STAFF_PROFILE",
                        "DOCTOR_PROFILE")
                .entityId(doctorId).recordLabel(profile.displayName()).actorAccountId(actor.accountId())
                .beforeState(before).afterState(after).build());
        return DoctorProfileMapper.toModel(profiles.save(profile));
    }

    @Override @Transactional
    public AdminMutationImpact assessImpact(UUID doctorId, String resourceType, UUID resourceId,
            Instant startsAt, Instant endsAt, AuthenticatedAccount actor) {
        if (actor == null || actor.accountRole() != AccountRole.ADMIN || actor.accountId() == null)
            throw new DoctorProfileException("ACCESS_DENIED");
        if (doctorId == null || startsAt == null || endsAt == null || !startsAt.isBefore(endsAt)
                || !"SHIFT".equals(resourceType)) throw invalid();
        guard.acquire(); requireDoctor(doctorId);
        var other=shifts.findAllByDoctorProfileIdOrderByStartsAtAscIdAsc(doctorId).stream()
                .filter(s -> resourceId == null || !s.id().equals(resourceId)).toList();
        return shiftImpact(doctorId, resourceId, startsAt, endsAt, other);
    }

    private AdminMutationImpact shiftImpact(UUID doctorId, UUID resourceId, Instant startsAt, Instant endsAt,
            java.util.List<DoctorWorkShiftJpaEntity> remainingShifts) {
        try {
            long bookings = appointments.findProtectedBookingIntervals(doctorId).stream()
                    .filter(b -> (startsAt == null || !covers(startsAt, endsAt, b.startsAt(), b.occupiedUntil()))
                            && remainingShifts.stream().noneMatch(s -> covers(s.startsAt(), s.endsAt(), b.startsAt(), b.occupiedUntil())))
                    .count();
            boolean care = visits.findActiveCareIntervals(doctorId).stream()
                    .anyMatch(c -> (startsAt == null || !covers(startsAt, endsAt, c.startsAt(), c.endsAt()))
                            && remainingShifts.stream().noneMatch(s -> covers(s.startsAt(), s.endsAt(), c.startsAt(), c.endsAt())));
            return new AdminMutationImpact("WORK_SHIFT", resourceId, bookings > 0 || care, bookings, care);
        } catch (RuntimeException failure) {
            throw new DoctorProfileException("DEPENDENCY_UNAVAILABLE", failure);
        }
    }

    private void requireDoctor(UUID doctorId) {
        if (!profiles.existsByIdAndDoctorTrue(doctorId)) throw new DoctorProfileException("DOCTOR_PROFILE_NOT_FOUND");
    }
    private static Map<String, Object> directoryState(boolean publiclyVisible, boolean acceptingNewBookings,
            boolean inActiveDirectory) {
        return Map.of("publiclyVisible", publiclyVisible, "acceptingNewBookings", acceptingNewBookings,
                "inActiveDirectory", inActiveDirectory);
    }
    private void validateDoctorRoom(UUID roomId) {
        if (roomId == null) return; // Keep older doctor-schedule callers compatible.
        if (rooms == null) return;
        var room = rooms.get(roomId);
        if (!room.active() || !"EXAMINATION".equals(room.type())) throw invalid();
    }
    private void ensureRoomAvailable(UUID roomId, Instant startsAt, Instant endsAt, UUID ignoreId) {
        if (roomId == null || jdbc == null) return;
        Integer count = jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM doctor_shifts WHERE room_id=? AND starts_at < ? AND ends_at > ? AND (?::uuid IS NULL OR id<>?))
                     + (SELECT count(*) FROM staff_work_shifts WHERE room_id=? AND starts_at < ? AND ends_at > ?)
                """, Integer.class, roomId, Timestamp.from(endsAt), Timestamp.from(startsAt), ignoreId, ignoreId,
                roomId, Timestamp.from(endsAt), Timestamp.from(startsAt));
        if (count != null && count > 0) throw new DoctorProfileException("DOCTOR_SHIFT_OVERLAP");
    }
    private boolean covers(Instant from, Instant to, Instant bookingFrom, Instant bookingTo) {
        return !from.isAfter(bookingFrom) && !to.isBefore(bookingTo);
    }
    private boolean overlaps(Instant startA, Instant endA, Instant startB, Instant endB) {
        return startA.isBefore(endB) && endA.isAfter(startB);
    }
    private void audit(UUID doctorId, UUID actorId, String type, UUID resourceId, String action,
            String before, String after, String correlationId) {
        audits.append(UUID.randomUUID(), doctorId, actorId, type, resourceId, action,
                before, after, clock.instant(), correlationId);
    }
    private String intervalJson(Instant startsAt, Instant endsAt) {
        return "{\"startsAt\":\"" + startsAt + "\",\"endsAt\":\"" + endsAt + "\"}";
    }
    private void validate(UUID doctorId, Instant startsAt, Instant endsAt, AuthenticatedAccount actor, String correlationId) {
        validateIds(doctorId, actor, correlationId);
        if (startsAt == null || endsAt == null || !startsAt.isBefore(endsAt)) throw invalid();
        requireFuture(startsAt);
    }
    private void validateIds(UUID doctorId, AuthenticatedAccount actor, String correlationId) {
        if (doctorId == null || actor == null || actor.accountId() == null || actor.accountRole() != AccountRole.ADMIN
                || correlationId == null || correlationId.isBlank() || correlationId.length() > 128)
            throw new DoctorProfileException("ACCESS_DENIED");
    }
    private void requireFuture(Instant startsAt) {
        if (startsAt.isBefore(clock.instant())) throw new DoctorProfileException("DOCTOR_RESOURCE_IN_PAST");
    }
    private DoctorProfileException invalid() { return new DoctorProfileException("DOCTOR_RESOURCE_REQUEST_INVALID"); }
}
