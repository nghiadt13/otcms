package fu.fa26.sep490.g58.otcms_be.patient.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.patient.service.DoctorCareRelationPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Temporary R3/KAN-28 adapter. Currently authorizes any active DOCTOR account without
 * checking a responsible-doctor/workspace relationship. Replace when that relationship
 * becomes available. Other roles and inactive accounts remain unauthorized.
 */
@Component
public class TemporaryR3DoctorCareRelationAdapter implements DoctorCareRelationPort {
    private final DoctorAccountAccessService doctors;
    public TemporaryR3DoctorCareRelationAdapter(DoctorAccountAccessService doctors) { this.doctors = doctors; }
    @Override public boolean isResponsibleDoctor(UUID doctorAccountId, UUID patientProfileId) {
        return doctorAccountId != null && patientProfileId != null && doctors.isActiveDoctorAccount(doctorAccountId);
    }
}
