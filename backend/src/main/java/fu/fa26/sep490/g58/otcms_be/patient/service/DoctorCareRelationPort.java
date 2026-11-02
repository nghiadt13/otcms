package fu.fa26.sep490.g58.otcms_be.patient.service;

import java.util.UUID;

public interface DoctorCareRelationPort {
    boolean isResponsibleDoctor(UUID doctorAccountId, UUID patientProfileId);
}
