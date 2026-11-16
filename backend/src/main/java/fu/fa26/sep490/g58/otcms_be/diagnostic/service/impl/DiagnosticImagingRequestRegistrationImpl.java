package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderExecutionPort.ExecutionStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ClinicalImagingOrderPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ImagingServiceCatalogPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticReadPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticImagingRequestRegistration;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosticImagingRequestRegistrationImpl implements DiagnosticImagingRequestRegistration {
    private final ClinicalServiceOrderExecutionPort requests;
    private final DoctorProfileIdentityQueryService identities;
    private final DoctorAccountAccessService accounts;
    private final VisitDiagnosticReadPort visits;
    private final ImagingServiceCatalogPort catalog;
    private final ClinicalImagingOrderPort orders;
    private final fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService rooms;

    public DiagnosticImagingRequestRegistrationImpl(ClinicalServiceOrderExecutionPort requests,
            DoctorProfileIdentityQueryService identities, DoctorAccountAccessService accounts,
            VisitDiagnosticReadPort visits, ImagingServiceCatalogPort catalog, ClinicalImagingOrderPort orders,
            fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService rooms) {
        this.requests = requests; this.identities = identities; this.accounts = accounts;
        this.visits = visits; this.catalog = catalog; this.orders = orders; this.rooms = rooms;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RegistrationResult register(RegisterImagingOrder command) {
        if (command == null || command.serviceOrderId() == null || command.visitId() == null || command.patientId() == null
                || command.orderingDoctorProfileId() == null || command.serviceId() == null || command.roomId() == null || command.bodyArea() == null
                || command.bodyArea().isBlank() || command.bodyArea().length() > 255 || command.clinicalReason() == null
                || command.clinicalReason().isBlank()) throw failure(DiagnosticOperationOutcome.SERVICE_NOT_ELIGIBLE);
        // Active same-room replay remains valid after execution/results. Cancelled requests do not fulfil V097.
        var existing = requests.find(command.serviceOrderId()).filter(r -> r.status() != ExecutionStatus.CANCELLED);
        if (existing.isPresent()) {
            var request = existing.get();
            if (!command.roomId().equals(request.roomId())) throw failure(DiagnosticOperationOutcome.STATE_CONFLICT);
            if (!command.visitId().equals(request.visitId()) || !command.patientId().equals(request.patientId())
                    || !command.serviceId().equals(request.serviceId()) || !Objects.equals(command.bodyArea(), request.bodyArea())
                    || !Objects.equals(command.clinicalReason(), request.clinicalReason())) throw failure(DiagnosticOperationOutcome.EVIDENCE_MISMATCH);
            var account = identities.findDoctorAccountId(command.orderingDoctorProfileId()).orElseThrow(() -> failure(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD));
            if (!account.equals(request.orderingDoctorAccountId())) throw failure(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
            return new RegistrationResult(request.orderId());
        }
        UUID orderingDoctorAccountId = identities.findDoctorAccountId(command.orderingDoctorProfileId())
                .orElseThrow(() -> failure(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD));
        if (!accounts.isActiveDoctorAccount(orderingDoctorAccountId)) throw failure(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
        var visit = visits.requireImagingOrderEligibility(command.visitId(), orderingDoctorAccountId);
        if (visit == null) throw failure(DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE);
        if (!command.visitId().equals(visit.visitId()) || !command.patientId().equals(visit.patientId())
                || !orderingDoctorAccountId.equals(visit.treatingDoctorId())) throw failure(DiagnosticOperationOutcome.EVIDENCE_MISMATCH);
        if (!visit.open() || !visit.eligible()) throw failure(DiagnosticOperationOutcome.VISIT_NOT_ELIGIBLE);
        var order = orders.requireOpenImagingOrder(command.serviceOrderId());
        if (order == null || !order.open() || !order.paid() || !command.visitId().equals(order.visitId()) || !command.serviceId().equals(order.serviceId())) {
            throw failure(DiagnosticOperationOutcome.EVIDENCE_MISMATCH);
        }
        var service = catalog.requireImagingService(command.serviceId(), null);
        if (service == null || !service.active() || !command.serviceId().equals(service.serviceId())
                || service.imagingType() == null || service.imagingType().isBlank()) throw failure(DiagnosticOperationOutcome.SERVICE_NOT_ELIGIBLE);
        try {
            rooms.requireEligibleRoom(command.roomId(), command.serviceId(), service.imagingType());
        } catch (fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException invalidRoom) {
            throw failure(DiagnosticOperationOutcome.ROOM_NOT_READY);
        }
        requests.assignImagingRoom(command.serviceOrderId(), command.visitId(), command.patientId(), command.serviceId(),
                command.roomId(), service.imagingType(), orderingDoctorAccountId);
        var persisted = requests.find(command.serviceOrderId()).filter(r -> r.status() != ExecutionStatus.CANCELLED)
                .orElseThrow(() -> failure(DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE));
        if (!command.roomId().equals(persisted.roomId())) throw failure(DiagnosticOperationOutcome.STATE_CONFLICT);
        if (!command.visitId().equals(persisted.visitId()) || !command.patientId().equals(persisted.patientId())
                || !orderingDoctorAccountId.equals(persisted.orderingDoctorAccountId()) || !command.serviceId().equals(persisted.serviceId())
                || !command.bodyArea().equals(persisted.bodyArea()) || !command.clinicalReason().equals(persisted.clinicalReason())) {
            throw failure(DiagnosticOperationOutcome.EVIDENCE_MISMATCH);
        }
        return new RegistrationResult(persisted.orderId());
    }

    private static DiagnosticOperationException failure(DiagnosticOperationOutcome outcome) { return new DiagnosticOperationException(outcome); }
}
