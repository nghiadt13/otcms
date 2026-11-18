package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderRoomService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitLifecycleDtos.ServiceOrderRoomTransfer;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitRoomJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitLifecycleRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService;
import fu.fa26.sep490.g58.otcms_be.visit.service.ServiceOrderRoomTransferService;
import java.time.Clock;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ServiceOrderRoomTransferServiceImpl implements ServiceOrderRoomTransferService {
    private final ReceptionistAccountAccessService receptionists;
    private final CoordinationGuardService guard;
    private final ClinicalServiceOrderRoomService orderRooms;
    private final ClinicVisitJpaRepository visits;
    private final VisitRoomJpaRepository rooms;
    private final ImagingRoomQueueService imagingRooms;
    private final VisitLifecycleRepository lifecycle;
    private final VisitAuditRecorder audit;
    private final Clock clock;

    public ServiceOrderRoomTransferServiceImpl(ReceptionistAccountAccessService receptionists,
            CoordinationGuardService guard, ClinicalServiceOrderRoomService orderRooms, ClinicVisitJpaRepository visits,
            VisitRoomJpaRepository rooms, ImagingRoomQueueService imagingRooms, VisitLifecycleRepository lifecycle,
            VisitAuditRecorder audit, Clock clock) {
        this.receptionists = receptionists;
        this.guard = guard;
        this.orderRooms = orderRooms;
        this.visits = visits;
        this.rooms = rooms;
        this.imagingRooms = imagingRooms;
        this.lifecycle = lifecycle;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ServiceOrderRoomTransfer transfer(UUID orderId, UUID newRoomId, String reason,
            AuthenticatedAccount actor, String correlationId) {
        if (actor == null || actor.accountId() == null || actor.accountRole() != AccountRole.RECEPTIONIST
                || !receptionists.isActiveReceptionistAccount(actor.accountId())) {
            throw new VisitOperationException("MSG-40");
        }
        if (orderId == null || newRoomId == null || reason == null || reason.isBlank()) {
            throw new VisitOperationException("MSG-39");
        }
        guard.acquire();
        var order = orderRooms.lockForTransfer(orderId).orElseThrow(() -> new VisitOperationException("MSG-41"));
        if (!order.movable() || newRoomId.equals(order.roomId())) throw new VisitOperationException("MSG-42");
        var visit = visits.findById(order.visitId()).orElseThrow(() -> new VisitOperationException("MSG-41"));
        if (!"OPEN".equals(visit.getStatus())) throw new VisitOperationException("MSG-42");
        VisitRoomJpaEntity room = rooms.findById(newRoomId).orElseThrow(() -> new VisitOperationException("MSG-41"));
        if (!room.isActive() || !room.getSupportedServiceIds().contains(order.serviceId())) {
            throw new VisitOperationException("MSG-42");
        }
        if ("IMAGING".equals(order.serviceType()) && order.imagingType() != null) {
            try {
                imagingRooms.requireEligibleRoom(newRoomId, order.serviceId(), order.imagingType());
            } catch (VisitOperationException unsuitable) {
                throw new VisitOperationException("MSG-42");
            }
        }
        // Same lock order as the queue (room rows by id), so a call-next on either room cannot interleave.
        List<UUID> toLock = List.of(order.roomId(), newRoomId).stream().sorted(Comparator.comparing(UUID::toString)).toList();
        toLock.forEach(id -> rooms.findByIdForUpdate(id).orElseThrow(() -> new VisitOperationException("MSG-41")));
        var entryStatus = lifecycle.findServiceEntryStatus(orderId);
        if (entryStatus.isPresent() && ("CALLED".equals(entryStatus.get()) || "IN_EXAMINATION".equals(entryStatus.get()))) {
            throw new VisitOperationException("MSG-42");
        }
        if (!orderRooms.changeRoom(orderId, newRoomId)) throw new VisitOperationException("MSG-42");
        lifecycle.moveServiceEntry(orderId, newRoomId);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("visitId", order.visitId().toString());
        metadata.put("fromRoomId", order.roomId().toString());
        metadata.put("toRoomId", newRoomId.toString());
        metadata.put("reason", reason.trim());
        audit.lifecycleEvent("SERVICE_ORDER_ROOM_TRANSFERRED", "SERVICE_ORDER", orderId, order.visitId(),
                actor.accountId(), clock.instant(), metadata, correlationId);
        return new ServiceOrderRoomTransfer(orderId, newRoomId, order.roomId());
    }

    @Override
    @Transactional
    public void followRoomChange(UUID orderId, UUID newRoomId) {
        if (orderId != null && newRoomId != null) lifecycle.moveServiceEntry(orderId, newRoomId);
    }
}
