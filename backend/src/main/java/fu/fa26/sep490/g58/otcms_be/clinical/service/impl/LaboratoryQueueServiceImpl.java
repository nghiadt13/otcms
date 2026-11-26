package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.billing.service.ClinicalRoundPaymentTimeQueryService;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ClinicalOrderingRoundJpaEntity;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ClinicalOrderingRoundRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.repository.ServiceOrderRepository;
import fu.fa26.sep490.g58.otcms_be.clinical.service.LaboratoryQueueService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientClinicalIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffRoomAssignmentQueryService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LaboratoryQueueServiceImpl implements LaboratoryQueueService {
    private final ServiceOrderRepository orders;
    private final ClinicalOrderingRoundRepository rounds;
    private final StaffRoomAssignmentQueryService assignments;
    private final ClinicalRoundPaymentTimeQueryService payments;
    private final PatientClinicalIdentityQueryService patients;
    private final Clock clock;

    public LaboratoryQueueServiceImpl(ServiceOrderRepository orders, ClinicalOrderingRoundRepository rounds,
            StaffRoomAssignmentQueryService assignments, ClinicalRoundPaymentTimeQueryService payments,
            PatientClinicalIdentityQueryService patients, Clock clock) {
        this.orders = orders; this.rounds = rounds; this.assignments = assignments; this.payments = payments; this.patients = patients; this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Item> today(AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null || actor.accountRole() != AccountRole.LAB_TECHNICIAN)
            throw new ClinicalOperationException("MSG-40");
        var now = clock.instant();
        var zone = ZoneId.of("Asia/Ho_Chi_Minh");
        var day = LocalDate.ofInstant(now, zone);
        var rows = orders.findLaboratoryAssignments(day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant())
                .stream().filter(row -> assignments.isLabTechnicianAssigned(actor.accountId(), row.getRoomId(), now))
                .collect(Collectors.toMap(ServiceOrderRepository.LaboratoryQueueRow::getOrderId, row -> row));
        if (rows.isEmpty()) return List.of();
        var found = orders.findAllById(rows.keySet());
        var names = patients.findClinicalIdentities(found.stream().map(o -> o.getPatientId()).distinct().toList());
        var paid = payments.confirmedPaymentTimes(found.stream().map(o -> o.getOrderingRoundId()).distinct().toList());
        var orderedAt = rounds.findAllById(found.stream().map(o -> o.getOrderingRoundId()).distinct().toList()).stream()
                .collect(Collectors.toMap(ClinicalOrderingRoundJpaEntity::getId, ClinicalOrderingRoundJpaEntity::getOrderedAt));
        return found.stream().map(o -> new Item(o.getId(), o.getOrderCode(),
                names.containsKey(o.getPatientId()) ? names.get(o.getPatientId()).fullName() : null,
                names.containsKey(o.getPatientId()) ? names.get(o.getPatientId()).dateOfBirth() : null,
                o.getServiceNameSnapshot(), o.getClinicalReason(), o.getPaymentStatus().name(), o.getOrderStatus().name(),
                rows.get(o.getId()).getLabStatus(), orderedAt.get(o.getOrderingRoundId()), paid.get(o.getOrderingRoundId())))
                .sorted(Comparator.comparingInt((Item item) -> item.orderStatus().equals("RESULTS_AVAILABLE") ? 2 : item.paymentStatus().equals("PAID") ? 0 : 1)
                        .thenComparing(Item::paidAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Item::orderedAt).thenComparing(Item::code)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Item> history(AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null || actor.accountRole() != AccountRole.LAB_TECHNICIAN)
            throw new ClinicalOperationException("MSG-40");
        var now = clock.instant();
        var rows = orders.findCompletedLaboratoryAssignments().stream()
                .filter(row -> assignments.isLabTechnicianAssigned(actor.accountId(), row.getRoomId(), now))
                .collect(Collectors.toMap(ServiceOrderRepository.LaboratoryQueueRow::getOrderId, row -> row));
        if (rows.isEmpty()) return List.of();
        var found = orders.findAllById(rows.keySet());
        var names = patients.findClinicalIdentities(found.stream().map(o -> o.getPatientId()).distinct().toList());
        var paid = payments.confirmedPaymentTimes(found.stream().map(o -> o.getOrderingRoundId()).distinct().toList());
        var orderedAt = rounds.findAllById(found.stream().map(o -> o.getOrderingRoundId()).distinct().toList()).stream()
                .collect(Collectors.toMap(ClinicalOrderingRoundJpaEntity::getId, ClinicalOrderingRoundJpaEntity::getOrderedAt));
        return found.stream().map(o -> new Item(o.getId(), o.getOrderCode(),
                names.containsKey(o.getPatientId()) ? names.get(o.getPatientId()).fullName() : null,
                names.containsKey(o.getPatientId()) ? names.get(o.getPatientId()).dateOfBirth() : null,
                o.getServiceNameSnapshot(), o.getClinicalReason(), o.getPaymentStatus().name(), o.getOrderStatus().name(),
                rows.get(o.getId()).getLabStatus(), orderedAt.get(o.getOrderingRoundId()), paid.get(o.getOrderingRoundId())))
                .sorted(Comparator.comparing(Item::orderedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Item::code)).toList();
    }
}
