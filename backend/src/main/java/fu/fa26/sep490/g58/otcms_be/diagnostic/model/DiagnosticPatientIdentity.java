package fu.fa26.sep490.g58.otcms_be.diagnostic.model;

import java.time.LocalDate;
import java.util.UUID;

/** Minimal patient identity shown only with imaging work assigned to the authenticated technician. */
public record DiagnosticPatientIdentity(UUID patientProfileId, String fullName,
        LocalDate dateOfBirth, String gender) { }
