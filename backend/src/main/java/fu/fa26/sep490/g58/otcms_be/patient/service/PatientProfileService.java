package fu.fa26.sep490.g58.otcms_be.patient.service;

import fu.fa26.sep490.g58.otcms_be.patient.model.PatientProfile;
import fu.fa26.sep490.g58.otcms_be.patient.model.ReceptionVerificationProfile;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;

public interface PatientProfileService {
    Page<PatientProfile> search(String query, int page, int size);
    PatientProfile create(String fullName, String phone, String phoneOwnerType, LocalDate dateOfBirth, String gender);
    ReceptionVerificationProfile getReceptionVerificationProfile(UUID actorAccountId, UUID patientProfileId);
    java.util.List<PatientProfile> matches(UUID actorAccountId, String phone, LocalDate dateOfBirth, String fullName);
    default PatientProfile create(UUID actorAccountId, String fullName, String phone, String phoneOwnerType,
            LocalDate dateOfBirth, String gender, String address, String nationalId, String identityDocumentType) {
        return create(actorAccountId, fullName, phone, phoneOwnerType, dateOfBirth, gender, address, nationalId,
                identityDocumentType, null, null);
    }
    PatientProfile create(UUID actorAccountId, String fullName, String phone, String phoneOwnerType,
            LocalDate dateOfBirth, String gender, String address, String nationalId, String identityDocumentType,
            String email, String emailOwnerType);
    PatientProfile detail(UUID actorAccountId, UUID patientProfileId);
    default PatientProfile update(UUID actorAccountId, UUID patientProfileId, String fullName, String phone, String phoneOwnerType,
            LocalDate dateOfBirth, String gender, String address, String nationalId, String identityDocumentType, Long version) {
        return update(actorAccountId, patientProfileId, fullName, phone, phoneOwnerType, dateOfBirth, gender, address,
                nationalId, identityDocumentType, null, null, version);
    }
    PatientProfile update(UUID actorAccountId, UUID patientProfileId, String fullName, String phone, String phoneOwnerType,
            LocalDate dateOfBirth, String gender, String address, String nationalId, String identityDocumentType,
            String email, String emailOwnerType, Long version);
}
