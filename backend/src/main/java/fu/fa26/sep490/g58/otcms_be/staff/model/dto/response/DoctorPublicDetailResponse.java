package fu.fa26.sep490.g58.otcms_be.staff.model.dto.response;

import java.util.List;
import java.util.UUID;

public record DoctorPublicDetailResponse(UUID doctorProfileId, String fullName, String photoUrl,
        String specialties, String qualifications, String biography, String consultationInformation,
        List<DoctorAvailabilityDayResponse> availability) {
    public DoctorPublicDetailResponse { availability = List.copyOf(availability); }
}
