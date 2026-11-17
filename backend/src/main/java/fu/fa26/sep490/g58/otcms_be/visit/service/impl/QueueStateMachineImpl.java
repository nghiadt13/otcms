package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorScheduleService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.QueueRoomDtos;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.QueueEntryJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.QueueRoomRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.QueueStateMachine;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueueStateMachineImpl implements QueueStateMachine {
    private final QueueRoomRepository queue;
    private final VisitRoomJpaRepository rooms;
    private final ReceptionistAccountAccessService receptionists;
    private final DoctorAccountAccessService doctors;
    private final DoctorScheduleService schedules;
    private final AppointmentBookingProperties booking;
    private final AuditLogWriter audit;
    private final Clock clock;

    public QueueStateMachineImpl(QueueRoomRepository queue, VisitRoomJpaRepository rooms,
            ReceptionistAccountAccessService receptionists, DoctorAccountAccessService doctors,
            DoctorScheduleService schedules, AppointmentBookingProperties booking, AuditLogWriter audit, Clock clock) {
        this.queue = queue; this.rooms = rooms; this.receptionists = receptionists; this.doctors = doctors;
        this.schedules = schedules; this.booking = booking; this.audit = audit; this.clock = clock;
    }

    @Override
    @Transactional
    public QueueRoomDtos.Entry callNext(UUID roomId, String stage, AuthenticatedAccount actor) {
        validateStage(stage);
        authorize(actor, roomId, false);
        List<QueueEntryJpaEntity> rows = lock(roomId, stage);
        if (rows.stream().anyMatch(q -> today(q) && ("CALLED".equals(q.getStatus()) || "IN_EXAMINATION".equals(q.getStatus())))) {
            throw error("MSG-42");
        }
        QueueEntryJpaEntity next = rows.stream().filter(this::today).filter(q -> "WAITING".equals(q.getStatus()))
                .findFirst().orElseThrow(() -> error("MSG-41"));
        next.call(clock.instant());
        return dto(next);
    }

    @Override
    @Transactional
    public QueueRoomDtos.Entry markAbsent(UUID entryId, AuthenticatedAccount actor) {
        QueueRoomRepository.Location location = requireLocation(entryId);
        authorize(actor, location.getRoomId(), false);
        QueueEntryJpaEntity entry = lockedEntry(entryId, location);
        if (!today(entry) || !"CALLED".equals(entry.getStatus())) throw error("MSG-42");
        entry.markAbsent(clock.instant());
        return dto(entry);
    }

    @Override
    @Transactional
    public QueueRoomDtos.Entry returnFromAbsence(UUID entryId, UUID beforeEntryId, String reason,
            AuthenticatedAccount actor) {
        if (reason == null || reason.isBlank()) throw error("MSG-39");
        QueueRoomRepository.Location location = requireLocation(entryId);
        authorize(actor, location.getRoomId(), true);
        List<QueueEntryJpaEntity> locked = lock(location.getRoomId(), location.getStage());
        QueueEntryJpaEntity entry = locked.stream().filter(q -> q.getId().equals(entryId)).findFirst()
                .orElseThrow(() -> error("MSG-41"));
        if (!"ABSENT".equals(entry.getStatus()) || entry.getAbsentAt() == null
                || !entry.getAbsentAt().atZone(booking.clinicTimeZone()).toLocalDate()
                        .equals(clock.instant().atZone(booking.clinicTimeZone()).toLocalDate())) throw error("MSG-42");
        List<QueueEntryJpaEntity> active = new ArrayList<>(locked.stream().filter(this::today)
                .filter(q -> List.of("WAITING", "CALLED", "IN_EXAMINATION").contains(q.getStatus())).toList());
        int position = active.size();
        if (beforeEntryId != null) {
            position = -1;
            for (int i = 0; i < active.size(); i++) if (active.get(i).getId().equals(beforeEntryId)) position = i;
            if (position < 0) throw error("MSG-41");
        }
        for (int i = position; i < active.size(); i++) {
            if (!"WAITING".equals(active.get(i).getStatus())) throw error("MSG-42");
        }
        long oldOrder = entry.getSortOrder();
        // End of queue: order like a fresh check-in (epoch ms, same rule as the V121 insert trigger),
        // so later check-ins stay behind the returned patient.
        Long order = position == active.size()
                ? Long.valueOf(Math.max(active.isEmpty() ? Long.MIN_VALUE : active.getLast().getSortOrder() + 1, clock.millis()))
                : insertionOrder(active, position);
        if (order == null) {
            for (int i = 0; i < active.size(); i++) active.get(i).changeSortOrder((i + 1L) * 1_000_000L);
            order = insertionOrder(active, position);
        }
        entry.returnToQueue(order);
        audit.record(AuditEntry.builder("queue_entries", "VISIT", "QUEUE_REPOSITION", "QUEUE_ENTRY")
                .entityId(entryId).actorAccountId(actor.accountId()).correctionReason(reason.trim())
                .beforeState(Map.of("sortOrder", oldOrder, "status", "ABSENT"))
                .afterState(Map.of("sortOrder", order, "status", "WAITING")).build());
        return dto(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QueueRoomDtos.Entry> list(UUID roomId, String stage, AuthenticatedAccount actor) {
        validateStage(stage);
        authorize(actor, roomId, false);
        if (!rooms.existsById(roomId)) throw error("MSG-41");
        return queue.findRoom(roomId, stage).stream().filter(this::today).map(QueueStateMachineImpl::dto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Integer> nowServing(UUID roomId) {
        if (!rooms.existsById(roomId)) throw error("MSG-41");
        return List.of("EXAM", "SERVICE").stream().flatMap(stage -> queue.findRoom(roomId, stage).stream())
                .filter(this::today).filter(q -> "CALLED".equals(q.getStatus()))
                .map(QueueEntryJpaEntity::getQueueNumber).filter(java.util.Objects::nonNull).toList();
    }

    private List<QueueEntryJpaEntity> lock(UUID roomId, String stage) {
        validateStage(stage);
        rooms.findByIdForUpdate(roomId).orElseThrow(() -> error("MSG-41"));
        return queue.lockRoom(roomId, stage);
    }

    private QueueEntryJpaEntity lockedEntry(UUID id, QueueRoomRepository.Location location) {
        return lock(location.getRoomId(), location.getStage()).stream()
                .filter(q -> q.getId().equals(id)).findFirst().orElseThrow(() -> error("MSG-41"));
    }

    private QueueRoomRepository.Location requireLocation(UUID id) {
        return queue.findLocation(id).filter(q -> q.getRoomId() != null).orElseThrow(() -> error("MSG-41"));
    }

    private void authorize(AuthenticatedAccount actor, UUID roomId, boolean receptionistOnly) {
        if (actor == null || actor.accountId() == null || roomId == null) throw error("MSG-40");
        if (actor.accountRole() == AccountRole.RECEPTIONIST && receptionists.isActiveReceptionistAccount(actor.accountId())) return;
        Instant now = clock.instant();
        if (!receptionistOnly && actor.accountRole() == AccountRole.DOCTOR && doctors.isActiveDoctorAccount(actor.accountId())
                && schedules.forAccount(actor.accountId(), now, now.plusSeconds(1)).stream()
                        .anyMatch(shift -> roomId.equals(shift.roomId()))) return;
        throw error("MSG-40");
    }

    /** Exam rows use their clinic date (a returning patient is moved to today); other rows use the entry day. */
    private boolean today(QueueEntryJpaEntity q) {
        java.time.LocalDate day = q.getClinicDate() != null ? q.getClinicDate()
                : q.getEnteredAt().atZone(booking.clinicTimeZone()).toLocalDate();
        return day.equals(clock.instant().atZone(booking.clinicTimeZone()).toLocalDate());
    }

    private static Long insertionOrder(List<QueueEntryJpaEntity> rows, int position) {
        if (rows.isEmpty()) return 1_000_000L;
        if (position == 0) return rows.getFirst().getSortOrder() > Long.MIN_VALUE + 1_000_000L
                ? rows.getFirst().getSortOrder() - 1_000_000L : null;
        if (position == rows.size()) return rows.getLast().getSortOrder() < Long.MAX_VALUE - 1_000_000L
                ? rows.getLast().getSortOrder() + 1_000_000L : null;
        long left = rows.get(position - 1).getSortOrder(), right = rows.get(position).getSortOrder();
        if (left >= right || left + 1 == right) return null;
        return (left & right) + ((left ^ right) >> 1);
    }

    private static void validateStage(String stage) {
        if (!"EXAM".equals(stage) && !"SERVICE".equals(stage)) throw error("MSG-39");
    }

    private static QueueRoomDtos.Entry dto(QueueEntryJpaEntity q) {
        return new QueueRoomDtos.Entry(q.getId(), q.getVisitId(), q.getQueueNumber(), q.getStage(), q.getStatus(), q.getSortOrder());
    }

    private static VisitOperationException error(String code) { return new VisitOperationException(code); }
}
