package fu.fa26.sep490.g58.otcms_be.patient.model.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record PatientProfileSummaryResponse(UUID patientProfileId, String fullName, String phoneCanonical,
        LocalDate dateOfBirth, String gender, boolean hasAccount) { }
