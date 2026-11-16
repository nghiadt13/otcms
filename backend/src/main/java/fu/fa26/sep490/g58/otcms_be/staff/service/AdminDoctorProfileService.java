package fu.fa26.sep490.g58.otcms_be.staff.service;

import java.util.List;
import java.util.UUID;
import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountTypeQueryService.DoctorAccountOption;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorProfile;

/** Admin-only directory reads and identity-preserving profile edits. */
public interface AdminDoctorProfileService {
    List<DoctorProfile> list();
    List<DoctorAccountOption> eligibleAccounts();
    record ProfileEdit(String fullName, String photoUrl, String workContact, String specialties,
            String qualifications, String biography, String consultationInformation) { }
    DoctorProfile update(UUID doctorId, ProfileEdit edit);
}
