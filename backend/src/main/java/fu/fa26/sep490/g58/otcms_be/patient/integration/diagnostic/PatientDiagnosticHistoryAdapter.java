package fu.fa26.sep490.g58.otcms_be.patient.integration.diagnostic;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.PatientDiagnosticHistoryPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.PatientHistoryAuthorization;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientProfileAccessService;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Patient owns active-account and confirmed self/dependent authorization. */
@Component
public class PatientDiagnosticHistoryAdapter implements PatientDiagnosticHistoryPort {
    private final PatientProfileAccessService access;
    public PatientDiagnosticHistoryAdapter(PatientProfileAccessService access) { this.access = access; }

    @Override
    public PatientHistoryAuthorization authorizePatientHistory(UUID actorId, UUID patientId) {
        boolean authorized = actorId != null && patientId != null && access.canAccess(actorId, patientId);
        return new PatientHistoryAuthorization(patientId, authorized ? "AUTHORIZED_PATIENT_PROFILE" : null, authorized);
    }
}
