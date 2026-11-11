package fu.fa26.sep490.g58.otcms_be.visit.integration.diagnostic;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.RoomEvidence;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.RoomReservation;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.RoomReservationPort;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitRoom;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.ImagingRoomWorkReservationJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ImagingRoomWorkReservationRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.ManageVisitRoomService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffShiftAdminService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class VisitRoomImagingAccessAdapter implements RoomReservationPort {
    private final ManageVisitRoomService rooms;
    private final ImagingRoomWorkReservationRepository reservations;
    private final StaffShiftAdminService shifts;
    private final Clock clock;

    public VisitRoomImagingAccessAdapter(ManageVisitRoomService rooms,
            ImagingRoomWorkReservationRepository reservations, StaffShiftAdminService shifts, Clock clock) {
        this.rooms = rooms;
        this.reservations = reservations;
        this.shifts = shifts;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public RoomEvidence requireEligibleImagingRoom(UUID roomId, String imagingType) {
        VisitRoom room = rooms.get(roomId);
        if (room == null) return null;
        Instant now = clock.instant();
        UUID technicianId = shifts.activeImagingTechnicianForRoom(roomId, now).orElse(null);
        boolean imagingRoom = "IMAGING".equalsIgnoreCase(room.type())
                || room.capabilities().contains("IMAGING_ROOM");
        boolean supportsType = imagingType != null && (room.capabilities().contains(imagingType.toUpperCase(java.util.Locale.ROOT))
                || room.capabilities().contains("IMAGING_ROOM") || !room.supportedServiceIds().isEmpty());
        return new RoomEvidence(room.id(), imagingType, room.active(), imagingRoom && supportsType,
                true, technicianId, room.updatedAt() == null ? 0 : room.updatedAt().toEpochMilli());
    }

    @Override
    @Transactional
    public RoomReservation reserveImagingCapacity(UUID requestId, UUID roomId, String imagingType,
            long expectedRoomVersion) {
        RoomEvidence current = requireEligibleImagingRoom(roomId, imagingType);
        if (current == null || !current.operational() || !current.compatible() || !current.hasCapacity()
                || current.roomVersion() != expectedRoomVersion) return new RoomReservation(requestId, roomId,
                current == null ? -1 : current.roomVersion(), false);
        var existing = reservations.findByRequestIdForUpdate(requestId);
        if (existing.isPresent()) {
            var reservation = existing.get();
            boolean same = roomId.equals(reservation.getRoomId()) && "RESERVED".equals(reservation.getState());
            return new RoomReservation(requestId, reservation.getRoomId(), current.roomVersion(), same);
        }
        reservations.saveAndFlush(new ImagingRoomWorkReservationJpaEntity(UUID.randomUUID(), requestId,
                roomId, imagingType));
        return new RoomReservation(requestId, roomId, current.roomVersion(), true);
    }

    @Override
    @Transactional
    public void releaseImagingCapacity(UUID requestId, String cause) {
        reservations.findByRequestIdForUpdate(requestId)
                .ifPresent(reservation -> reservation.release(cause, clock.instant()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasImagingCapacityReservation(UUID requestId) {
        return requestId != null && reservations.existsByRequestIdAndState(requestId, "RESERVED");
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasEligibleAlternative(UUID excludedRoomId, String imagingType) {
        return rooms.listAll().stream().filter(room -> !room.id().equals(excludedRoomId))
                .map(room -> requireEligibleImagingRoom(room.id(), imagingType))
                .anyMatch(room -> room != null && room.operational() && room.compatible() && room.hasCapacity());
    }
}
