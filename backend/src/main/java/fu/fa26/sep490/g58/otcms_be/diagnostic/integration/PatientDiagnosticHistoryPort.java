package fu.fa26.sep490.g58.otcms_be.diagnostic.integration;

import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.PatientHistoryAuthorization;
import java.util.UUID;

/** Patient-owned authorization for a Patient's own or approved dependent's imaging history. */
public interface PatientDiagnosticHistoryPort {
    PatientHistoryAuthorization authorizePatientHistory(UUID actorId, UUID patientId);
}
