package fu.fa26.sep490.g58.otcms_be.patient.model;

import java.time.LocalDate;
import java.util.UUID;

public class PatientProfile {
    private final UUID id;
    private final String fullName;
    private final String phoneCanonical;
    private final LocalDate dateOfBirth;
    private final String gender;

    public PatientProfile(UUID id, String fullName, String phoneCanonical, LocalDate dateOfBirth, String gender) {
        this.id = id;
        this.fullName = fullName;
        this.phoneCanonical = phoneCanonical;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
    }

    public UUID getId() {
        return id;
    }

    public UUID id() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String fullName() {
        return fullName;
    }

    public String getPhoneCanonical() {
        return phoneCanonical;
    }

    public String phoneCanonical() {
        return phoneCanonical;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public LocalDate dateOfBirth() {
        return dateOfBirth;
    }

    public String getGender() {
        return gender;
    }

    public String gender() {
        return gender;
    }
}
