package fu.fa26.sep490.g58.otcms_be.staff.model.dto.response;

import java.util.List;
import java.util.UUID;

public record StaffProfileResponse(UUID id, UUID accountId, String username, String role,
        String displayName, String photoUrl, String workContact, String status, Boolean isPublic,
        List<String> allowedActions, DoctorInfo doctor, String publicUrl) {
    public record DoctorInfo(List<String> specialties, String qualifications, String biography,
            String consultationInfo, boolean isPublic) { }
}
