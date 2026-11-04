package fu.fa26.sep490.g58.otcms_be.patient.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.patient.model.PatientProfile;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.request.UpdatePatientProfileRequest;

public interface MyPatientProfileService {
    PatientProfile get(AuthenticatedAccount actor);
    PatientProfile update(AuthenticatedAccount actor, UpdatePatientProfileRequest request);
}
