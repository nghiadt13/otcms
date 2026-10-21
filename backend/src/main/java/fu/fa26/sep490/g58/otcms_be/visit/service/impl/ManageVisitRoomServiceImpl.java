package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.common.exception.CoordinationGuardUnavailableException;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.mapper.VisitRoomMapper;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitRoom;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitRoomJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.ManageVisitRoomService;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManageVisitRoomServiceImpl implements ManageVisitRoomService {
    private final CoordinationGuardService guard;
    private final VisitRoomJpaRepository rooms;
    private final Clock clock;

    public ManageVisitRoomServiceImpl(CoordinationGuardService guard, VisitRoomJpaRepository rooms, Clock clock) {
        this.guard = guard;
        this.rooms = rooms;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<VisitRoom> listAll() {
        try {
            return rooms.findAllByOrderByRoomCodeAscIdAsc().stream().map(VisitRoomMapper::toModel).toList();
        } catch (DataAccessException exception) {
            throw new VisitOperationException("DEPENDENCY_UNAVAILABLE", exception);
        }
    }

    @Override
    @Transactional
    public VisitRoom create(String roomCode, String displayLabel, List<String> capabilities,
            UUID actorAccountId, String correlationId) {
        validateActorAndCorrelation(actorAccountId, correlationId);
        String normalizedCode = normalizeRoomCode(roomCode);
        String normalizedLabel = normalizeLabel(displayLabel);
        List<String> normalizedCapabilities = normalizeCapabilities(capabilities);
        acquireGuard();
        try {
            if (rooms.existsByRoomCode(normalizedCode)) throw new VisitOperationException("DUPLICATE_ROOM_CODE");
            Instant now = clock.instant();
            VisitRoomJpaEntity room = rooms.saveAndFlush(new VisitRoomJpaEntity(UUID.randomUUID(), normalizedCode,
                    normalizedLabel, true, now, now, Set.copyOf(normalizedCapabilities)));
            appendAudit(room.getId(), actorAccountId, "CREATED", null, correlationId, now);
            return VisitRoomMapper.toModel(room);
        } catch (DataAccessException exception) {
            if (hasSqlState(exception, "23505")) throw new VisitOperationException("DUPLICATE_ROOM_CODE", exception);
            throw new VisitOperationException("DEPENDENCY_UNAVAILABLE", exception);
        }
    }

    @Override
    @Transactional
    public VisitRoom update(UUID roomId, String displayLabel, List<String> capabilities,
            UUID actorAccountId, String correlationId) {
        validateRoomCommand(roomId, actorAccountId, correlationId);
        String normalizedLabel = normalizeLabel(displayLabel);
        List<String> normalizedCapabilities = normalizeCapabilities(capabilities);
        acquireGuard();
        try {
            VisitRoomJpaEntity current = requireRoomForUpdate(roomId);
            Set<String> desiredCapabilities = Set.copyOf(normalizedCapabilities);
            if (!current.getCapabilities().equals(desiredCapabilities)
                    && rooms.hasBlockingUse(roomId, clock.instant())) {
                throw new VisitOperationException("ROOM_HAS_ACTIVE_RESERVATIONS");
            }
            if (current.getDisplayLabel().equals(normalizedLabel)
                    && current.getCapabilities().equals(desiredCapabilities)) return VisitRoomMapper.toModel(current);
            String beforeState = rooms.snapshot(roomId);
            Instant now = clock.instant();
            current.update(normalizedLabel, desiredCapabilities, now);
            VisitRoomJpaEntity updated = rooms.saveAndFlush(current);
            appendAudit(roomId, actorAccountId, "UPDATED", beforeState, correlationId, now);
            return VisitRoomMapper.toModel(updated);
        } catch (VisitOperationException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new VisitOperationException("DEPENDENCY_UNAVAILABLE", exception);
        }
    }

    @Override
    @Transactional
    public void deactivate(UUID roomId, UUID actorAccountId, String correlationId) {
        setActive(roomId, false, actorAccountId, correlationId);
    }

    @Override
    @Transactional
    public void activate(UUID roomId, UUID actorAccountId, String correlationId) {
        setActive(roomId, true, actorAccountId, correlationId);
    }

    private void setActive(UUID roomId, boolean active, UUID actorAccountId, String correlationId) {
        validateRoomCommand(roomId, actorAccountId, correlationId);
        acquireGuard();
        try {
            VisitRoomJpaEntity current = requireRoomForUpdate(roomId);
            if (!active && current.isActive() && rooms.hasBlockingUse(roomId, clock.instant())) {
                throw new VisitOperationException("ROOM_HAS_ACTIVE_RESERVATIONS");
            }
            if (current.isActive() == active) return;
            Instant now = clock.instant();
            String beforeState = rooms.snapshot(roomId);
            current.setActive(active, now);
            rooms.saveAndFlush(current);
            appendAudit(roomId, actorAccountId, active ? "ACTIVATED" : "DEACTIVATED",
                    beforeState, correlationId, now);
        } catch (VisitOperationException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new VisitOperationException("DEPENDENCY_UNAVAILABLE", exception);
        }
    }

    private VisitRoomJpaEntity requireRoomForUpdate(UUID roomId) {
        VisitRoomJpaEntity room = rooms.findByIdForUpdate(roomId).orElse(null);
        if (room == null) throw new VisitOperationException("ROOM_NOT_FOUND");
        return room;
    }

    private void appendAudit(UUID roomId, UUID actorAccountId, String action, String beforeState,
            String correlationId, java.time.Instant occurredAt) {
        String afterState = rooms.snapshot(roomId);
        rooms.appendManagementAudit(UUID.randomUUID(), roomId, actorAccountId, action, beforeState,
                afterState, occurredAt, correlationId);
    }

    private void acquireGuard() {
        try {
            guard.acquire();
        } catch (CoordinationGuardUnavailableException exception) {
            throw new VisitOperationException("DEPENDENCY_UNAVAILABLE", exception);
        } catch (DataAccessException exception) {
            throw new VisitOperationException("DEPENDENCY_UNAVAILABLE", exception);
        }
    }

    private static String normalizeRoomCode(String roomCode) {
        if (roomCode == null || !roomCode.trim().matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")) {
            throw new VisitOperationException("VALIDATION_FAILED");
        }
        return roomCode.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeLabel(String displayLabel) {
        if (displayLabel == null) throw new VisitOperationException("VALIDATION_FAILED");
        String label = displayLabel.trim();
        if (label.isEmpty() || label.length() > 128) throw new VisitOperationException("VALIDATION_FAILED");
        return label;
    }

    private static List<String> normalizeCapabilities(List<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty() || capabilities.size() > 32) {
            throw new VisitOperationException("VALIDATION_FAILED");
        }
        List<String> normalized = new ArrayList<>();
        for (String capability : capabilities) {
            if (capability == null || !capability.matches("[A-Z][A-Z0-9_]{0,63}")
                    || "NONE".equals(capability) || normalized.contains(capability)) {
                throw new VisitOperationException("VALIDATION_FAILED");
            }
            normalized.add(capability);
        }
        normalized.sort(Comparator.naturalOrder());
        return List.copyOf(normalized);
    }

    private static void validateRoomCommand(UUID roomId, UUID actorAccountId, String correlationId) {
        if (roomId == null) throw new VisitOperationException("VALIDATION_FAILED");
        validateActorAndCorrelation(actorAccountId, correlationId);
    }

    private static void validateActorAndCorrelation(UUID actorAccountId, String correlationId) {
        if (actorAccountId == null || correlationId == null || correlationId.isBlank()
                || correlationId.length() > 128) throw new VisitOperationException("VALIDATION_FAILED");
    }

    private static boolean hasSqlState(Throwable failure, String expected) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) {
                for (SQLException next = sqlException; next != null; next = next.getNextException()) {
                    if (expected.equals(next.getSQLState())) return true;
                }
            }
        }
        return false;
    }
}
