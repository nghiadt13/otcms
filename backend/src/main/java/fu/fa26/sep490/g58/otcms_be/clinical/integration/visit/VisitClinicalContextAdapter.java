package fu.fa26.sep490.g58.otcms_be.clinical.integration.visit;

import fu.fa26.sep490.g58.otcms_be.clinical.service.VisitClinicalContextPort;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitClinicalCareContextService;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Translates Visit-owned current-care facts without accessing Visit persistence. */
@Component
public class VisitClinicalContextAdapter implements VisitClinicalContextPort {
    private final VisitClinicalCareContextService visits;

    public VisitClinicalContextAdapter(VisitClinicalCareContextService visits) {
        this.visits = visits;
    }

    @Override
    public java.util.Map<UUID, ClinicalVisitContext> getVisitContexts(java.util.Collection<UUID> visitIds) {
        return visits.findClinicalCareContexts(visitIds).values().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                context -> context.visitId(), context -> new ClinicalVisitContext(context.visitId(), context.patientProfileId(),
                        context.responsibleDoctorProfileId(), context.clinicalWorkAllowed())));
    }

    @Override
    public ClinicalVisitContext getVisitContext(UUID visitId) {
        return visits.findClinicalCareContext(visitId)
                .map(context -> new ClinicalVisitContext(context.visitId(), context.patientProfileId(),
                        context.responsibleDoctorProfileId(), context.clinicalWorkAllowed()))
                .orElse(null);
    }
}
