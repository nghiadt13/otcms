package fu.fa26.sep490.g58.otcms_be.patient.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.patient.model.PatientProfile;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.request.CreateMyPatientProfileRequest;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.request.UpdatePatientProfileRequest;

public interface MyPatientProfileService {
    PatientProfile get(AuthenticatedAccount actor);
    /** Tạo hồ sơ "Bản thân" lần đầu cho tài khoản bệnh nhân chưa có hồ sơ. */
    PatientProfile create(AuthenticatedAccount actor, CreateMyPatientProfileRequest request);
    PatientProfile update(AuthenticatedAccount actor, UpdatePatientProfileRequest request);
}
