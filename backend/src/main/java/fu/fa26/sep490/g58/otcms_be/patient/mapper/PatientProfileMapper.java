package fu.fa26.sep490.g58.otcms_be.patient.mapper;

import fu.fa26.sep490.g58.otcms_be.patient.model.dto.response.PatientProfileResponse;
import fu.fa26.sep490.g58.otcms_be.patient.model.PatientProfile;
import org.springframework.stereotype.Component;

@Component
public class PatientProfileMapper {
    public PatientProfileResponse toResponse(PatientProfile profile) {
        return new PatientProfileResponse(profile.id(), profile.fullName(), profile.phoneCanonical(), profile.dateOfBirth());
    }
}
