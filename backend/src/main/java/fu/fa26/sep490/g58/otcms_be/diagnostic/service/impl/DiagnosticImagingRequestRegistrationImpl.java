package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.exception.DiagnosticOperationException;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ClinicalImagingOrderPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ImagingServiceCatalogPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.VisitDiagnosticReadPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticRequestStatus;
import fu.fa26.sep490.g58.otcms_be.diagnostic.repository.DiagnosticRequestJpaRepository;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticImagingRequestRegistration;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosticImagingRequestRegistrationImpl implements DiagnosticImagingRequestRegistration {
    private final DiagnosticRequestJpaRepository requests;
    private final DoctorProfileIdentityQueryService identities;
    private final DoctorAccountAccessService accounts;
    private final VisitDiagnosticReadPort visits;
    private final ImagingServiceCatalogPort catalog;
    private final ClinicalImagingOrderPort orders;

    public DiagnosticImagingRequestRegistrationImpl(DiagnosticRequestJpaRepository requests,
            DoctorProfileIdentityQueryService identities, DoctorAccountAccessService accounts,
            VisitDiagnosticReadPort visits, ImagingServiceCatalogPort catalog, ClinicalImagingOrderPort orders) {
        this.requests = requests; this.identities = identities; this.accounts = accounts;
        this.visits = visits; this.catalog = catalog; this.orders = orders;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RegistrationResult register(RegisterImagingOrder command) {
        if (command == null || command.serviceOrderId() == null || command.visitId() == null || command.patientId() == null
                || command.orderingDoctorProfileId() == null || command.serviceId() == null || command.bodyArea() == null
                || command.bodyArea().isBlank() || command.bodyArea().length() > 255 || command.clinicalReason() == null
                || command.clinicalReason().isBlank()) throw failure(DiagnosticOperationOutcome.SERVICE_NOT_ELIGIBLE);
        // Idempotent replay requires no current catalog lookup; an existing request may already have results or be cancelled.
        var existing = requests.findFirstByServiceOrderIdOrderByCreatedAtDescIdDesc(command.serviceOrderId());
        if (existing.isPresent()) {
            var request = existing.get();
            if (!command.visitId().equals(request.getVisitId()) || !command.patientId().equals(request.getPatientId())
                    || !command.serviceId().equals(request.getServiceId()) || !Objects.equals(command.bodyArea(), request.getBodyArea())
                    || !Objects.equals(command.clinicalReason(), request.getClinicalReason())) throw failure(DiagnosticOperationOutcome.EVIDENCE_MISMATCH);
            var account = identities.findDoctorAccountId(command.orderingDoctorProfileId()).orElseThrow(() -> failure(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD));
            if (!account.equals(request.getOrderingDoctorId())) throw failure(DiagnosticOperationOutcome.UNAUTHORIZED_RECORD);
            return new RegistrationResult(request.getId());
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
        if (order == null || !order.open() || !command.visitId().equals(order.visitId()) || !command.serviceId().equals(order.serviceId())) {
            throw failure(DiagnosticOperationOutcome.EVIDENCE_MISMATCH);
        }
        var service = catalog.requireImagingService(command.serviceId(), null);
        if (service == null || !service.active() || !command.serviceId().equals(service.serviceId())
                || service.imagingType() == null || service.imagingType().isBlank()) throw failure(DiagnosticOperationOutcome.SERVICE_NOT_ELIGIBLE);
        requests.insertUnassigned(UUID.randomUUID(), command.visitId(), command.patientId(), orderingDoctorAccountId,
                command.serviceId(), service.imagingType(), command.bodyArea(), command.clinicalReason(), command.serviceOrderId());
        var persisted = requests.findFirstByServiceOrderIdOrderByCreatedAtDescIdDesc(command.serviceOrderId())
                .orElseThrow(() -> failure(DiagnosticOperationOutcome.DEPENDENCY_UNAVAILABLE));
        if (!command.visitId().equals(persisted.getVisitId()) || !command.patientId().equals(persisted.getPatientId())
                || !orderingDoctorAccountId.equals(persisted.getOrderingDoctorId()) || !command.serviceId().equals(persisted.getServiceId())
                || !command.bodyArea().equals(persisted.getBodyArea()) || !command.clinicalReason().equals(persisted.getClinicalReason())) {
            throw failure(DiagnosticOperationOutcome.EVIDENCE_MISMATCH);
        }
        return new RegistrationResult(persisted.getId());
    }

    private static DiagnosticOperationException failure(DiagnosticOperationOutcome outcome) { return new DiagnosticOperationException(outcome); }
}
