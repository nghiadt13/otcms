package fu.fa26.sep490.g58.otcms_be.patient.model.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record PatientProfileDetailResponse(UUID patientProfileId, String fullName, String phoneCanonical,
        String phoneOwnerType, LocalDate dateOfBirth, String gender, String address, String nationalId,
        String identityDocumentType, boolean hasAccount, long version) { }
