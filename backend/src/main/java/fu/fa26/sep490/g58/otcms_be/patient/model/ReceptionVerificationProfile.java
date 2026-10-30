package fu.fa26.sep490.g58.otcms_be.patient.model;

import java.time.LocalDate;
import java.util.UUID;

/** Purpose-limited Patient identity projection for Receptionist check-in verification. */
public record ReceptionVerificationProfile(UUID patientProfileId, String fullName, LocalDate dateOfBirth) { }
