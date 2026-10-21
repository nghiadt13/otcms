package fu.fa26.sep490.g58.otcms_be.patient.service;

import fu.fa26.sep490.g58.otcms_be.patient.model.PatientProfile;
import java.time.LocalDate;
import org.springframework.data.domain.Page;

public interface PatientProfileService {
    Page<PatientProfile> search(String query, int page, int size);
    PatientProfile create(String fullName, String phone, String phoneOwnerType, LocalDate dateOfBirth, String gender);
}
