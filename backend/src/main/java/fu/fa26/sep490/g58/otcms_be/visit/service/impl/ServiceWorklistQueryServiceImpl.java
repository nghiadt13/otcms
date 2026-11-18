package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.billing.service.BillingQueryService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderQueueService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderQueueService.QueueOrder;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientClinicalIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.ServiceWorklistDtos;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.QueueEntryJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.QueueRoomRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ServiceQueueRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService;
import fu.fa26.sep490.g58.otcms_be.visit.service.ServiceWorklistQueryService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ServiceWorklistQueryServiceImpl implements ServiceWorklistQueryService {
    private final QueueRoomRepository queue;
    private final VisitRoomJpaRepository rooms;
    private final ClinicVisitJpaRepository visits;
    private final ClinicalServiceOrderQueueService orders;
    private final BillingQueryService billing;
    private final PatientClinicalIdentityQueryService patients;
    private final ReceptionistAccountAccessService receptionists;
    private final ImagingRoomQueueService imagingRooms;
    private final AppointmentBookingProperties booking;
    private final Clock clock;

    public ServiceWorklistQueryServiceImpl(QueueRoomRepository queue, VisitRoomJpaRepository rooms,
            ClinicVisitJpaRepository visits, ClinicalServiceOrderQueueService orders, BillingQueryService billing,
            PatientClinicalIdentityQueryService patients, ReceptionistAccountAccessService receptionists,
            ImagingRoomQueueService imagingRooms, AppointmentBookingProperties booking, Clock clock) {
        this.queue = queue;
        this.rooms = rooms;
        this.visits = visits;
        this.orders = orders;
        this.billing = billing;
        this.patients = patients;
        this.receptionists = receptionists;
        this.imagingRooms = imagingRooms;
        this.booking = booking;
        this.clock = clock;
    }

    @Override
    public ServiceWorklistDtos.Worklist worklist(UUID roomId, AuthenticatedAccount actor) {
        authorize(actor, roomId);
        if (!rooms.existsById(roomId)) throw new VisitOperationException("MSG-41");
        LocalDate today = clock.instant().atZone(booking.clinicTimeZone()).toLocalDate();
        List<QueueEntryJpaEntity> entries = queue.findRoom(roomId, QueueEntryJpaEntity.STAGE_SERVICE).stream()
                .filter(entry -> ServiceQueueRepository.OPEN.contains(entry.getStatus()))
                .filter(entry -> today.equals(entry.getClinicDate() != null ? entry.getClinicDate()
                        : entry.getEnteredAt().atZone(booking.clinicTimeZone()).toLocalDate())).toList();
        if (entries.isEmpty()) return new ServiceWorklistDtos.Worklist(roomId, List.of());
        var patientIds = entries.stream().map(entry -> visits.findById(entry.getVisitId()).map(v -> v.getPatientProfileId()).orElse(null))
                .filter(java.util.Objects::nonNull).distinct().toList();
        var identities = patients.findClinicalIdentities(patientIds);
        List<ServiceWorklistDtos.Entry> items = entries.stream().map(entry -> {
            UUID patientId = visits.findById(entry.getVisitId()).map(v -> v.getPatientProfileId()).orElse(null);
            var identity = patientId == null ? null : identities.get(patientId);
            var patient = identity == null ? new ServiceWorklistDtos.Patient(patientId, null, null, null, null)
                    : new ServiceWorklistDtos.Patient(patientId, identity.fullName(), identity.dateOfBirth(),
                            identity.dateOfBirth() == null ? null : Period.between(identity.dateOfBirth(), today).getYears(),
                            identity.gender());
            var roomOrders = orders.findByVisit(entry.getVisitId()).stream()
                    .filter(order -> roomId.equals(order.roomId()) && !"CANCELLED".equals(order.orderStatus()))
                    .map(this::orderDto).toList();
            return new ServiceWorklistDtos.Entry(entry.getId(), entry.getVisitId(), entry.getQueueNumber(), entry.getStatus(),
                    entry.getSortOrder(), entry.getQueuedAt(), entry.getCalledAt(), patient, roomOrders);
        }).toList();
        return new ServiceWorklistDtos.Worklist(roomId, items);
    }

    private ServiceWorklistDtos.Order orderDto(QueueOrder order) {
        return new ServiceWorklistDtos.Order(order.orderId(), order.orderCode(), order.serviceId(), order.serviceName(),
                order.serviceType(), order.orderStatus(), billing.isServiceOrderPaid(order.orderId()), order.clinicalReason(),
                order.bodyArea(), order.instructions(), order.executionStatus());
    }

    private void authorize(AuthenticatedAccount actor, UUID roomId) {
        if (actor == null || actor.accountId() == null || roomId == null) throw new VisitOperationException("MSG-40");
        if (actor.accountRole() == AccountRole.RECEPTIONIST && receptionists.isActiveReceptionistAccount(actor.accountId())) return;
        if (actor.accountRole() == AccountRole.IMAGING_TECHNICIAN && imagingRooms.isAssignedTechnician(actor.accountId(), roomId)) return;
        throw new VisitOperationException("MSG-40");
    }
}
