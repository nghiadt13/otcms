package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicServiceCatalogPort;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalOrderingService.OrderRequest;
import fu.fa26.sep490.g58.otcms_be.clinical.service.VisitClinicalContextPort;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Shared authorization/catalog validation for generic ordering and UC38. No persistence writes. */
@Service
public class ClinicalOrderPreparationService {
    private final VisitClinicalContextPort visits;
    private final ClinicServiceCatalogPort catalog;

    public ClinicalOrderPreparationService(VisitClinicalContextPort visits, ClinicServiceCatalogPort catalog) {
        this.visits = visits;
        this.catalog = catalog;
    }

    public VisitClinicalContextPort.ClinicalVisitContext requireCurrentCare(UUID visitId, UUID doctorId) {
        if (visitId == null) throw new ClinicalOperationException("MSG-39");
        if (doctorId == null) throw new ClinicalOperationException("MSG-40");
        var context = visits.getVisitContext(visitId);
        if (context == null) throw new ClinicalOperationException("MSG-41");
        if (context.responsibleDoctorId() == null || !context.responsibleDoctorId().equals(doctorId)) {
            throw new ClinicalOperationException("MSG-40");
        }
        if (context.patientId() == null) throw new ClinicalOperationException("MSG-39");
        if (!context.clinicalWorkAllowed()) throw new ClinicalOperationException("MSG-42");
        return context;
    }

    public List<ClinicalOrderingTransactionService.OrderSnapshot> prepare(List<OrderRequest> requests, boolean imagingOnly) {
        String invalid = imagingOnly ? "MSG-39" : "MSG-08";
        if (requests == null || requests.isEmpty()) throw new ClinicalOperationException(invalid);
        return requests.stream().map(request -> {
            if (request == null || request.serviceId() == null) throw new ClinicalOperationException("MSG-39");
            var service = catalog.getService(request.serviceId());
            if (service != null && !request.serviceId().equals(service.serviceId())) throw new ClinicalOperationException("MSG-39");
            if (service == null || !service.active() || blank(service.name()) || service.type() == null
                    || service.currentPrice() == null || service.currentPrice().signum() < 0
                    || blank(request.clinicalReason())
                    || (service.type() == ClinicalServiceType.IMAGING && blank(request.bodyArea()))) {
                throw new ClinicalOperationException(invalid);
            }
            if (imagingOnly) {
                if (service.type() != ClinicalServiceType.IMAGING || request.bodyArea().length() > 255
                        || service.name().length() > 255) throw new ClinicalOperationException("MSG-39");
                try {
                    var price = service.currentPrice().setScale(2, RoundingMode.UNNECESSARY);
                    if (price.precision() > 12) throw new ClinicalOperationException("MSG-39");
                    service = new ClinicServiceCatalogPort.ClinicalServiceSnapshot(service.serviceId(), service.name(),
                            service.type(), price, service.active());
                } catch (ArithmeticException invalidPrice) {
                    throw new ClinicalOperationException("MSG-39");
                }
            }
            return new ClinicalOrderingTransactionService.OrderSnapshot(request, service);
        }).toList();
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
