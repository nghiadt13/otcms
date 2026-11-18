package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderPaymentStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalLaboratoryDispatchService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.VisitClinicalContextPort;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry;
import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.visit.service.LaboratoryRoomQueryService;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicalLaboratoryDispatchServiceImpl implements ClinicalLaboratoryDispatchService {
    private final ServiceOrderRepository orders;
    private final VisitClinicalContextPort visits;
    private final LaboratoryRoomQueryService rooms;
    private final CoordinationGuardService guard;
    private final AuditLogWriter audit;
    private final Clock clock;

    public ClinicalLaboratoryDispatchServiceImpl(ServiceOrderRepository orders, VisitClinicalContextPort visits,
            LaboratoryRoomQueryService rooms, CoordinationGuardService guard, AuditLogWriter audit, Clock clock) {
        this.orders = orders; this.visits = visits; this.rooms = rooms; this.guard = guard; this.audit = audit; this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<LaboratoryRoomQueryService.Room> eligibleRooms(UUID orderId, AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null || actor.accountRole() != AccountRole.RECEPTIONIST)
            throw new ClinicalOperationException("MSG-40");
        if (orderId == null) throw new ClinicalOperationException("MSG-39");
        var order = orders.findById(orderId).orElseThrow(() -> new ClinicalOperationException("MSG-41"));
        if (order.getServiceType() != ClinicalServiceType.LABORATORY || order.getOrderStatus() != ServiceOrderStatus.ORDERED)
            throw new ClinicalOperationException("MSG-42");
        var context = visits.getVisitContext(order.getVisitId());
        if (context == null || !context.clinicalWorkAllowed() || !order.getVisitId().equals(context.visitId())
                || !order.getPatientId().equals(context.patientId())
                || !order.getRequestingDoctorId().equals(context.responsibleDoctorId()))
            throw new ClinicalOperationException("MSG-42");
        return rooms.eligibleRooms(order.getServiceId());
    }

    @Override
    @Transactional
    public void assign(UUID orderId, UUID roomId, AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null || actor.accountRole() != AccountRole.RECEPTIONIST)
            throw new ClinicalOperationException("MSG-40");
        if (orderId == null || roomId == null) throw new ClinicalOperationException("MSG-39");
        guard.acquire();
        var order = orders.findById(orderId).orElseThrow(() -> new ClinicalOperationException("MSG-41"));
        if (order.getServiceType() != ClinicalServiceType.LABORATORY || order.getOrderStatus() != ServiceOrderStatus.ORDERED)
            throw new ClinicalOperationException("MSG-42");
        if (order.getPaymentStatus() != ServiceOrderPaymentStatus.PAID) throw new ClinicalOperationException("MSG-95");
        var context = visits.getVisitContext(order.getVisitId());
        if (context == null || !context.clinicalWorkAllowed() || !order.getVisitId().equals(context.visitId())
                || !order.getPatientId().equals(context.patientId())
                || !order.getRequestingDoctorId().equals(context.responsibleDoctorId()))
            throw new ClinicalOperationException("MSG-42");
        var assignment = orders.findLaboratoryAssignment(orderId).orElseThrow(() -> new ClinicalOperationException("MSG-42"));
        if (assignment.getLabStatus() != null) {
            if ("ASSIGNED".equals(assignment.getLabStatus()) && roomId.equals(assignment.getRoomId())) return;
            throw new ClinicalOperationException("MSG-42");
        }
        if (!rooms.isEligibleRoom(roomId, order.getServiceId())) throw new ClinicalOperationException("MSG-39");
        var now = clock.instant();
        if (orders.assignLaboratoryRoom(orderId, roomId, now) != 1) throw new ClinicalOperationException("MSG-42");
        audit.record(AuditEntry.builder("laboratory", "LABORATORY", "ASSIGN_ROOM", "SERVICE_ORDER")
                .entityId(orderId).actorAccountId(actor.accountId()).actorType("RECEPTIONIST")
                .afterState(Map.of("roomId", roomId.toString())).occurredAt(now).build());
    }
}
