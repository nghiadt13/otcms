package fu.fa26.sep490.g58.otcms_be.patient.service;

import fu.fa26.sep490.g58.otcms_be.patient.model.dto.DependentDtos.*;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.response.PatientProfileDetailResponse;
import java.util.UUID;

public interface DependentProfileService {
    LinkResult add(UUID actor, AddDependentRequest request);
    LinkResult verify(UUID actor, UUID linkId, String code);
    LinkResult resend(UUID actor, UUID linkId);
    Items<ProfileItem> list(UUID actor);
    PatientProfileDetailResponse detail(UUID actor, UUID profileId);
    PatientProfileDetailResponse update(UUID actor, UUID profileId, UpdateDependentRequest request);
    void unlink(UUID actor, UUID profileId);
}
