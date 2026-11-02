package fu.fa26.sep490.g58.otcms_be.staff.model.dto.request;

import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.StaffProfileResponse.DoctorInfo;
import java.util.UUID;

public record StaffProfileInput(UUID accountId, String displayName, String workContact, DoctorInfo doctor) { }
