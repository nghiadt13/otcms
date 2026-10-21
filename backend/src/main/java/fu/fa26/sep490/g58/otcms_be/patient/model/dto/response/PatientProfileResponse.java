package fu.fa26.sep490.g58.otcms_be.patient.model.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public class PatientProfileResponse {
    private final UUID patientProfileId;
    private final String fullName;
    private final String phoneCanonical;
    private final LocalDate dateOfBirth;
    public PatientProfileResponse(UUID patientProfileId, String fullName, String phoneCanonical, LocalDate dateOfBirth) {
        this.patientProfileId = patientProfileId;
        this.fullName = fullName;
        this.phoneCanonical = phoneCanonical;
        this.dateOfBirth = dateOfBirth;
    }
    public UUID getPatientProfileId() { return patientProfileId; }
    public UUID patientProfileId() { return patientProfileId; }
    public String getFullName() { return fullName; }
    public String fullName() { return fullName; }
    public String getPhoneCanonical() { return phoneCanonical; }
    public String phoneCanonical() { return phoneCanonical; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public LocalDate dateOfBirth() { return dateOfBirth; }
}
