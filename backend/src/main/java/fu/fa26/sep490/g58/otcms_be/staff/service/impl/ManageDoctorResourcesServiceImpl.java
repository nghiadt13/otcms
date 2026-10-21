package fu.fa26.sep490.g58.otcms_be.staff.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentMutationImpactService;
import fu.fa26.sep490.g58.otcms_be.common.model.AdminMutationImpact;
import fu.fa26.sep490.g58.otcms_be.common.exception.ResourceMutationBlockedException;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitMutationImpactService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.staff.exception.DoctorProfileException;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.DoctorIntervalResponse;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorProfile;
import fu.fa26.sep490.g58.otcms_be.staff.mapper.DoctorProfileMapper;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.DoctorOperationalTimeOffJpaEntity;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.DoctorWorkShiftJpaEntity;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorOperationalTimeOffRepository;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorProfileRepository;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorWorkShiftRepository;
import fu.fa26.sep490.g58.otcms_be.staff.repository.StaffResourceManagementAuditRepository;
import fu.fa26.sep490.g58.otcms_be.staff.service.ManageDoctorResourcesService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManageDoctorResourcesServiceImpl implements ManageDoctorResourcesService {
    private final DoctorProfileRepository profiles;
    private final DoctorWorkShiftRepository shifts;
    private final DoctorOperationalTimeOffRepository timeOffs;
    private final StaffResourceManagementAuditRepository audits;
    private final AppointmentMutationImpactService appointments;
    private final VisitMutationImpactService visits;
    private final CoordinationGuardService guard;
    private final Clock clock;

    public ManageDoctorResourcesServiceImpl(DoctorProfileRepository profiles, DoctorWorkShiftRepository shifts,
            DoctorOperationalTimeOffRepository timeOffs, StaffResourceManagementAuditRepository audits,
            AppointmentMutationImpactService appointments, VisitMutationImpactService visits,
            CoordinationGuardService guard, Clock clock) {
        this.profiles=profiles; this.shifts=shifts; this.timeOffs=timeOffs; this.audits=audits;
        this.appointments=appointments; this.visits=visits; this.guard=guard; this.clock=clock;
    }

    @Override @Transactional
    public DoctorIntervalResponse createShift(UUID doctorId, Instant startsAt, Instant endsAt, AuthenticatedAccount actor, String correlationId) {
        validate(doctorId, startsAt, endsAt, actor, correlationId);
        guard.acquire(); requireDoctor(doctorId);
        var existing = shifts.findAllByDoctorProfileIdOrderByStartsAtAscIdAsc(doctorId);
        if (existing.stream().anyMatch(s -> overlaps(startsAt, endsAt, s.startsAt(), s.endsAt())))
            throw new DoctorProfileException("DOCTOR_SHIFT_OVERLAP");
        UUID id = UUID.randomUUID();
        var saved = shifts.save(new DoctorWorkShiftJpaEntity(id, doctorId, startsAt, endsAt));
        audit(doctorId, actor.accountId(), "WORK_SHIFT", id, "CREATED", null, intervalJson(startsAt, endsAt), correlationId);
        return new DoctorIntervalResponse(saved.id(), saved.doctorProfileId(), saved.startsAt(), saved.endsAt());
    }

    @Override @Transactional
    public void deleteShift(UUID doctorId, UUID shiftId, AuthenticatedAccount actor, String correlationId) {
        validateIds(doctorId, actor, correlationId); if (shiftId == null) throw invalid();
        guard.acquire(); requireDoctor(doctorId);
        var shift = shifts.findByIdAndDoctorProfileId(shiftId, doctorId).orElseThrow(() -> new DoctorProfileException("DOCTOR_SHIFT_NOT_FOUND"));
        var other = shifts.findAllByDoctorProfileIdOrderByStartsAtAscIdAsc(doctorId).stream().filter(s -> !s.id().equals(shiftId)).toList();
        AdminMutationImpact impact = shiftImpact(doctorId, shiftId, null, null, other);
        if (impact.isBlocked()) throw new ResourceMutationBlockedException(impact);
        shifts.delete(shift);
        audit(doctorId, actor.accountId(), "WORK_SHIFT", shiftId, "DELETED", intervalJson(shift.startsAt(), shift.endsAt()), "{}", correlationId);
    }

    @Override @Transactional
    public DoctorIntervalResponse updateShift(UUID doctorId, UUID shiftId, Instant startsAt, Instant endsAt,
            AuthenticatedAccount actor, String correlationId) {
        validate(doctorId, startsAt, endsAt, actor, correlationId); if (shiftId == null) throw invalid();
        guard.acquire(); requireDoctor(doctorId);
        var shift=shifts.findByIdAndDoctorProfileId(shiftId, doctorId).orElseThrow(() -> new DoctorProfileException("DOCTOR_SHIFT_NOT_FOUND"));
        var other=shifts.findAllByDoctorProfileIdOrderByStartsAtAscIdAsc(doctorId).stream().filter(s -> !s.id().equals(shiftId)).toList();
        if (other.stream().anyMatch(s -> overlaps(startsAt, endsAt, s.startsAt(), s.endsAt())))
            throw new DoctorProfileException("DOCTOR_SHIFT_OVERLAP");
        AdminMutationImpact impact = shiftImpact(doctorId, shiftId, startsAt, endsAt, other);
        if (impact.isBlocked()) throw new ResourceMutationBlockedException(impact);
        String before=intervalJson(shift.startsAt(), shift.endsAt()); shift.updateInterval(startsAt, endsAt);
        var saved=shifts.save(shift);
        audit(doctorId, actor.accountId(), "WORK_SHIFT", shiftId, "UPDATED", before, intervalJson(startsAt, endsAt), correlationId);
        return new DoctorIntervalResponse(saved.id(), saved.doctorProfileId(), saved.startsAt(), saved.endsAt());
    }

    @Override @Transactional
    public DoctorIntervalResponse createTimeOff(UUID doctorId, Instant startsAt, Instant endsAt, AuthenticatedAccount actor, String correlationId) {
        validate(doctorId, startsAt, endsAt, actor, correlationId);
        guard.acquire(); requireDoctor(doctorId);
        AdminMutationImpact impact = timeOffImpact(doctorId, null, startsAt, endsAt);
        if (impact.isBlocked()) throw new ResourceMutationBlockedException(impact);
        UUID id = UUID.randomUUID();
        var saved = timeOffs.save(new DoctorOperationalTimeOffJpaEntity(id, doctorId, startsAt, endsAt));
        audit(doctorId, actor.accountId(), "TIME_OFF", id, "CREATED", null, intervalJson(startsAt, endsAt), correlationId);
        return new DoctorIntervalResponse(saved.id(), saved.doctorProfileId(), saved.startsAt(), saved.endsAt());
    }

    @Override @Transactional
    public void deleteTimeOff(UUID doctorId, UUID timeOffId, AuthenticatedAccount actor, String correlationId) {
        validateIds(doctorId, actor, correlationId); if (timeOffId == null) throw invalid();
        guard.acquire(); requireDoctor(doctorId);
        var item = timeOffs.findByIdAndDoctorProfileId(timeOffId, doctorId)
                .orElseThrow(() -> new DoctorProfileException("DOCTOR_TIME_OFF_NOT_FOUND"));
        timeOffs.delete(item);
        audit(doctorId, actor.accountId(), "TIME_OFF", timeOffId, "DELETED", intervalJson(item.startsAt(), item.endsAt()), "{}", correlationId);
    }

    @Override @Transactional
    public DoctorIntervalResponse updateTimeOff(UUID doctorId, UUID timeOffId, Instant startsAt, Instant endsAt,
            AuthenticatedAccount actor, String correlationId) {
        validate(doctorId, startsAt, endsAt, actor, correlationId); if (timeOffId == null) throw invalid();
        guard.acquire(); requireDoctor(doctorId);
        var item=timeOffs.findByIdAndDoctorProfileId(timeOffId, doctorId)
                .orElseThrow(() -> new DoctorProfileException("DOCTOR_TIME_OFF_NOT_FOUND"));
        AdminMutationImpact impact = timeOffImpact(doctorId, timeOffId, startsAt, endsAt);
        if (impact.isBlocked()) throw new ResourceMutationBlockedException(impact);
        String before=intervalJson(item.startsAt(), item.endsAt()); item.updateInterval(startsAt, endsAt);
        var saved=timeOffs.save(item);
        audit(doctorId, actor.accountId(), "TIME_OFF", timeOffId, "UPDATED", before, intervalJson(startsAt, endsAt), correlationId);
        return new DoctorIntervalResponse(saved.id(), saved.doctorProfileId(), saved.startsAt(), saved.endsAt());
    }

    @Override @Transactional
    public DoctorProfile updateDirectoryState(UUID doctorId, boolean publiclyVisible, boolean acceptingNewBookings,
            boolean inActiveDirectory, AuthenticatedAccount actor, String correlationId) {
        validateIds(doctorId, actor, correlationId); guard.acquire();
        var profile = profiles.findById(doctorId).orElseThrow(() -> new DoctorProfileException("DOCTOR_PROFILE_NOT_FOUND"));
        String before = "{\"publiclyVisible\":"+profile.publiclyVisible()+",\"acceptingNewBookings\":"
                +profile.acceptingNewBookings()+",\"inActiveDirectory\":"+profile.inActiveDirectory()+"}";
        profile.updateDirectoryState(publiclyVisible, acceptingNewBookings, inActiveDirectory);
        String after = "{\"publiclyVisible\":"+publiclyVisible+",\"acceptingNewBookings\":"
                +acceptingNewBookings+",\"inActiveDirectory\":"+inActiveDirectory+"}";
        audit(doctorId, actor.accountId(), "DOCTOR_PROFILE", doctorId, "UPDATED", before, after, correlationId);
        return DoctorProfileMapper.toModel(profiles.save(profile));
    }

    @Override @Transactional
    public AdminMutationImpact assessImpact(UUID doctorId, String resourceType, UUID resourceId,
            Instant startsAt, Instant endsAt, AuthenticatedAccount actor) {
        if (actor == null || actor.accountRole() != AccountRole.ADMIN || actor.accountId() == null)
            throw new DoctorProfileException("ACCESS_DENIED");
        if (doctorId == null || startsAt == null || endsAt == null || !startsAt.isBefore(endsAt)
                || !("SHIFT".equals(resourceType) || "TIME_OFF".equals(resourceType))) throw invalid();
        guard.acquire(); requireDoctor(doctorId);
        if ("TIME_OFF".equals(resourceType)) return timeOffImpact(doctorId, resourceId, startsAt, endsAt);
        var other=shifts.findAllByDoctorProfileIdOrderByStartsAtAscIdAsc(doctorId).stream()
                .filter(s -> resourceId == null || !s.id().equals(resourceId)).toList();
        return shiftImpact(doctorId, resourceId, startsAt, endsAt, other);
    }

    @Override @Transactional
    public AdminMutationImpact assessTimeOffRemovalImpact(UUID doctorId, UUID timeOffId, AuthenticatedAccount actor) {
        if (actor == null || actor.accountRole() != AccountRole.ADMIN || actor.accountId() == null)
            throw new DoctorProfileException("ACCESS_DENIED");
        if (doctorId == null || timeOffId == null) throw invalid();
        guard.acquire(); requireDoctor(doctorId);
        timeOffs.findByIdAndDoctorProfileId(timeOffId, doctorId)
                .orElseThrow(() -> new DoctorProfileException("DOCTOR_TIME_OFF_NOT_FOUND"));
        return new AdminMutationImpact("TIME_OFF", timeOffId, false, 0, false);
    }

    private AdminMutationImpact timeOffImpact(UUID doctorId, UUID resourceId, Instant startsAt, Instant endsAt) {
        try {
            long bookings = appointments.countBookingsOverlappingTimeOff(doctorId, startsAt, endsAt);
            boolean care = visits.countActiveCareOverlapping(doctorId, startsAt, endsAt) > 0;
            return new AdminMutationImpact("TIME_OFF", resourceId, bookings > 0 || care, bookings, care);
        } catch (RuntimeException failure) {
            throw new DoctorProfileException("DEPENDENCY_UNAVAILABLE", failure);
        }
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
        if (!profiles.existsById(doctorId)) throw new DoctorProfileException("DOCTOR_PROFILE_NOT_FOUND");
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
    }
    private void validateIds(UUID doctorId, AuthenticatedAccount actor, String correlationId) {
        if (doctorId == null || actor == null || actor.accountId() == null || actor.accountRole() != AccountRole.ADMIN
                || correlationId == null || correlationId.isBlank() || correlationId.length() > 128)
            throw new DoctorProfileException("ACCESS_DENIED");
    }
    private DoctorProfileException invalid() { return new DoctorProfileException("DOCTOR_RESOURCE_REQUEST_INVALID"); }
}
