package fu.fa26.sep490.g58.otcms_be.patient.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.patient.service.DoctorCareRelationPort;
import java.util.UUID;

/**
 * Retired R3/KAN-28 shortcut. Not a Spring bean and never authorizes a caller.
 * Production uses VisitDoctorCareRelationAdapter with authoritative current-care evidence.
 */
@Deprecated(forRemoval = true)
public class TemporaryR3DoctorCareRelationAdapter implements DoctorCareRelationPort {
    public TemporaryR3DoctorCareRelationAdapter(DoctorAccountAccessService doctors) { }
    @Override public boolean isResponsibleDoctor(UUID doctorAccountId, UUID patientProfileId) {
        // Retired, not a production bean. Fail closed for any legacy Java caller.
        return false;
    }
}
