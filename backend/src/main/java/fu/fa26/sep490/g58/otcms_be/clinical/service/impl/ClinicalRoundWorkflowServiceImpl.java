package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.*;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.*;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalRoundWorkflowService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticImagingRequestRegistration;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticOrderStatusQueryService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticOrderStatusQueryService.OrderDiagnosticStatus;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientClinicalIdentityQueryService;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicalRoundWorkflowServiceImpl implements ClinicalRoundWorkflowService {
    private final ServiceOrderRepository orders;
    private final ClinicalOrderingRoundRepository rounds;
    private final ClinicalOrderPreparationService preparation;
    private final PatientClinicalIdentityQueryService patients;
    private final DiagnosticOrderStatusQueryService statuses;
    private final DiagnosticImagingRequestRegistration registration;
    private final CoordinationGuardService guard;
    private final fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService rooms;
    private final fu.fa26.sep490.g58.otcms_be.clinical.service.VisitClinicalContextPort visits;
    public ClinicalRoundWorkflowServiceImpl(ServiceOrderRepository orders, ClinicalOrderingRoundRepository rounds,
            ClinicalOrderPreparationService preparation, PatientClinicalIdentityQueryService patients,
            DiagnosticOrderStatusQueryService statuses, DiagnosticImagingRequestRegistration registration,
            CoordinationGuardService guard,
            fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService rooms,
            fu.fa26.sep490.g58.otcms_be.clinical.service.VisitClinicalContextPort visits) {
        this.orders = orders; this.rounds = rounds; this.preparation = preparation; this.patients = patients;
        this.statuses = statuses; this.registration = registration; this.guard = guard; this.rooms = rooms; this.visits = visits;
    }
    @Override
    @Transactional(readOnly = true)
    public List<Order> listDoctorOrders(UUID visitId, UUID doctorProfileId) {
        preparation.requireCurrentCare(visitId, doctorProfileId);
        var rows = orders.findByVisitId(visitId).stream()
                .filter(order -> order.getServiceType() == ClinicalServiceType.IMAGING).toList();
        var roundsById = loadRoundsById(rows.stream().map(ServiceOrderJpaEntity::getOrderingRoundId).distinct().toList());
        var diagnostic = statuses.findByServiceOrders(rows.stream().map(ServiceOrderJpaEntity::getId).toList());
        return rows.stream().map(order -> view(order, roundsById.get(order.getOrderingRoundId()).getRoundNumber(), diagnostic))
                .sorted(Comparator.comparingInt(Order::roundNumber).thenComparing(Order::orderCode)).toList();
    }
    @Override
    @Transactional(readOnly = true)
    public List<Order> listDoctorLaboratoryOrders(UUID visitId, UUID doctorProfileId) {
        preparation.requireCurrentCare(visitId, doctorProfileId);
        var rows = orders.findVisitOrdersByType(visitId, ClinicalServiceType.LABORATORY);
        var roundsById = loadRoundsById(rows.stream().map(ServiceOrderJpaEntity::getOrderingRoundId).distinct().toList());
        return rows.stream().map(order -> view(order, roundsById.get(order.getOrderingRoundId()).getRoundNumber(), Map.of())).toList();
    }
    @Override
    @Transactional(readOnly = true)
    public RoundPage listReceptionRounds(AuthenticatedAccount actor, int page, int size) {
        requireReceptionist(actor);
        if (page < 0 || size < 1 || size > 100) throw new ClinicalOperationException("MSG-39");
        var result = rounds.findAll(PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "orderedAt").and(Sort.by("id"))));
        var ids = result.getContent().stream().map(ClinicalOrderingRoundJpaEntity::getId).toList();
        var rows = ids.isEmpty() ? List.<ServiceOrderJpaEntity>of() : orders.findByOrderingRoundIdIn(ids);
        var grouped = rows.stream().collect(Collectors.groupingBy(ServiceOrderJpaEntity::getOrderingRoundId));
        // Every order of a round carries the same patient; the round row has no patient column.
        var patientByRound = rows.stream().collect(Collectors.toMap(ServiceOrderJpaEntity::getOrderingRoundId,
                ServiceOrderJpaEntity::getPatientId, (first, second) -> first));
        var identity = patients.findClinicalIdentities(patientByRound.values().stream().distinct().toList());
        var diagnostic = statuses.findByServiceOrders(rows.stream().map(ServiceOrderJpaEntity::getId).toList());
        var items = result.getContent().stream().map(round -> {
            var patientId = patientByRound.get(round.getId());
            return new Round(round.getId(), round.getVisitId(), patientId,
                    identity.containsKey(patientId) ? identity.get(patientId).fullName() : null,
                    round.getRoundNumber(), round.getOrderedAt(), grouped.getOrDefault(round.getId(), List.of()).stream()
                            .map(order -> view(order, round.getRoundNumber(), diagnostic)).sorted(Comparator.comparing(Order::orderCode)).toList());
        }).toList();
        return new RoundPage(page, size, result.hasNext(), items);
    }
    @Override
    @Transactional(readOnly = true)
    public List<RoomOptions> eligibleImagingRooms(List<UUID> serviceOrderIds, AuthenticatedAccount actor) {
        requireReceptionist(actor);
        if (serviceOrderIds == null || serviceOrderIds.isEmpty() || serviceOrderIds.size() > 200
                || serviceOrderIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(serviceOrderIds).size() != serviceOrderIds.size()) throw new ClinicalOperationException("MSG-39");
        var rows = orders.findAllById(serviceOrderIds);
        if (rows.size() != serviceOrderIds.size()) throw new ClinicalOperationException("MSG-41");
        var contexts = visits.getVisitContexts(rows.stream().map(ServiceOrderJpaEntity::getVisitId).distinct().toList());
        for (var order : rows) {
            var context = contexts.get(order.getVisitId());
            if (order.getServiceType() != ClinicalServiceType.IMAGING || order.getOrderStatus() != ServiceOrderStatus.ORDERED
                    || context == null || !context.clinicalWorkAllowed()
                    || !order.getVisitId().equals(context.visitId()) || !order.getPatientId().equals(context.patientId())
                    || !order.getRequestingDoctorId().equals(context.responsibleDoctorId())) throw new ClinicalOperationException("MSG-42");
        }
        final Map<UUID, List<fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService.QueueRoom>> options;
        try {
            options = rooms.findEligibleRooms(rows.stream().map(ServiceOrderJpaEntity::getServiceId).distinct().toList());
        } catch (fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException invalidService) {
            throw new ClinicalOperationException("MSG-42");
        }
        var byId = rows.stream().collect(Collectors.toMap(ServiceOrderJpaEntity::getId, row -> row));
        return serviceOrderIds.stream().map(id -> new RoomOptions(id,
                options.getOrDefault(byId.get(id).getServiceId(), List.of()))).toList();
    }

    @Override
    @Transactional
    public List<UUID> dispatch(UUID roundId, AuthenticatedAccount actor, List<RoomAssignment> assignments) {
        requireReceptionist(actor);
        if (assignments == null || assignments.isEmpty() || assignments.size() > 200
                || assignments.stream().anyMatch(item -> item == null || item.serviceOrderId() == null || item.roomId() == null)) {
            throw new ClinicalOperationException("MSG-39");
        }
        Map<UUID, UUID> selected = new LinkedHashMap<>();
        for (var assignment : assignments) {
            if (selected.putIfAbsent(assignment.serviceOrderId(), assignment.roomId()) != null) throw new ClinicalOperationException("MSG-39");
        }
        guard.acquire();
        var round = rounds.findById(roundId).orElseThrow(() -> new ClinicalOperationException("MSG-41"));
        var roundOrders = orders.findByOrderingRoundId(roundId);
        var effective = roundOrders.stream()
                .filter(order -> order.getServiceType() == ClinicalServiceType.IMAGING && order.getOrderStatus() != ServiceOrderStatus.CANCELLED).toList();
        if (effective.isEmpty()) throw new ClinicalOperationException("MSG-42");
        var patientId = roundOrders.get(0).getPatientId(); // all orders of a round share one patient
        var existing = statuses.findByServiceOrders(effective.stream().map(ServiceOrderJpaEntity::getId).toList()).entrySet().stream()
                .filter(entry -> !"CANCELLED".equals(entry.getValue().status()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        // A submitted round includes all new work. Already dispatched work may be included for an exact replay.
        if (!effective.stream().map(ServiceOrderJpaEntity::getId).collect(Collectors.toSet()).containsAll(selected.keySet())
                || effective.stream().anyMatch(order -> !selected.containsKey(order.getId()) && !existing.containsKey(order.getId()))) {
            throw new ClinicalOperationException("MSG-39");
        }
        for (var order : effective) {
            if (order.getPaymentStatus() != ServiceOrderPaymentStatus.PAID
                    || (order.getOrderStatus() != ServiceOrderStatus.ORDERED && !existing.containsKey(order.getId()))
                    || !roundId.equals(order.getOrderingRoundId()) || !round.getVisitId().equals(order.getVisitId())
                    || !patientId.equals(order.getPatientId())
                    || !round.getOrderedByDoctorId().equals(order.getRequestingDoctorId())) throw new ClinicalOperationException("MSG-42");
        }
        return effective.stream().filter(order -> selected.containsKey(order.getId())).map(order -> registration.register(
                new DiagnosticImagingRequestRegistration.RegisterImagingOrder(order.getId(), order.getVisitId(), order.getPatientId(),
                        order.getRequestingDoctorId(), order.getServiceId(), selected.get(order.getId()),
                        order.getBodyArea(), order.getClinicalReason())).requestId()).toList();
    }
    private Map<UUID, ClinicalOrderingRoundJpaEntity> loadRoundsById(List<UUID> roundIds) {
        return rounds.findAllById(roundIds).stream().collect(Collectors.toMap(ClinicalOrderingRoundJpaEntity::getId, round -> round));
    }
    private static Order view(ServiceOrderJpaEntity order, int roundNumber, Map<UUID, OrderDiagnosticStatus> diagnostic) {
        return new Order(order.getId(), order.getOrderCode(), order.getOrderingRoundId(), roundNumber, order.getServiceId(),
                order.getServiceNameSnapshot(), order.getServiceType(), order.getClinicalReason(), order.getBodyArea(), order.getPriceSnapshot(),
                order.getOrderStatus(), order.getPaymentStatus(), diagnostic.get(order.getId()));
    }
    private static void requireReceptionist(AuthenticatedAccount actor) {
        if (actor == null || actor.accountRole() != AccountRole.RECEPTIONIST) throw new ClinicalOperationException("MSG-40");
    }
}
