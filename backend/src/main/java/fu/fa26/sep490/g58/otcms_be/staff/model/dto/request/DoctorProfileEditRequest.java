package fu.fa26.sep490.g58.otcms_be.staff.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Account and directory flags cannot be changed through a profile edit. */
public record DoctorProfileEditRequest(@NotBlank @Size(max = 160) String fullName,
        @Size(max = 2048) String photoUrl, @Size(max = 254) String workContact,
        String specialties, String qualifications, String biography, String consultationInformation) { }
