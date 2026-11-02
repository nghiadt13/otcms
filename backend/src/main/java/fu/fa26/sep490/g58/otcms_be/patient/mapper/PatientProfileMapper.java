package fu.fa26.sep490.g58.otcms_be.patient.mapper;

import fu.fa26.sep490.g58.otcms_be.patient.model.dto.response.PatientProfileResponse;
import fu.fa26.sep490.g58.otcms_be.patient.model.PatientProfile;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.response.PatientProfileSummaryResponse;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.response.PatientProfileDetailResponse;
import org.springframework.stereotype.Component;

@Component
public class PatientProfileMapper {
    public PatientProfileResponse toResponse(PatientProfile profile) {
        return new PatientProfileResponse(profile.id(), profile.fullName(), profile.phoneCanonical(), profile.dateOfBirth());
    }
    public PatientProfileSummaryResponse toSummary(PatientProfile profile) {
        return new PatientProfileSummaryResponse(profile.id(), profile.fullName(), profile.phoneCanonical(),
                profile.dateOfBirth(), profile.gender(), profile.hasAccount());
    }
    public PatientProfileDetailResponse toDetail(PatientProfile profile) {
        return new PatientProfileDetailResponse(profile.id(), profile.fullName(), profile.phoneCanonical(),
                profile.phoneOwnerType(), profile.dateOfBirth(), profile.gender(), profile.address(),
                profile.nationalId(), profile.identityDocumentType(), profile.hasAccount(), profile.version());
    }
}
