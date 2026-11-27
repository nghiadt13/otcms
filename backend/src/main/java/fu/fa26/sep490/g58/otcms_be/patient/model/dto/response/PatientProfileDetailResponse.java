package fu.fa26.sep490.g58.otcms_be.patient.model.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record PatientProfileDetailResponse(UUID patientProfileId, String fullName, String phoneCanonical,
        String phoneOwnerType, LocalDate dateOfBirth, String gender, String address, String nationalId,
        String identityDocumentType, String email, String emailOwnerType, boolean hasAccount, long version) {
    public PatientProfileDetailResponse(UUID patientProfileId, String fullName, String phoneCanonical,
            String phoneOwnerType, LocalDate dateOfBirth, String gender, String address, String nationalId,
            String identityDocumentType, boolean hasAccount, long version) {
        this(patientProfileId, fullName, phoneCanonical, phoneOwnerType, dateOfBirth, gender, address, nationalId,
                identityDocumentType, null, null, hasAccount, version);
    }
}
