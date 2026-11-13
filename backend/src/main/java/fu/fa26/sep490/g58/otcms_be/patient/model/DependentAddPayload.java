package fu.fa26.sep490.g58.otcms_be.patient.model;

import java.time.LocalDate;

/** Sensitive data pending OTP; never log or audit. */
public record DependentAddPayload(String fullName, LocalDate dateOfBirth, String gender, String nationalId,
        String identityDocumentType, String relationship) { }
