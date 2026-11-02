package fu.fa26.sep490.g58.otcms_be.patient.model;

import java.time.LocalDate;
import java.util.UUID;

public class PatientProfile {
    private final UUID id;
    private final String fullName;
    private final String phoneCanonical;
    private final LocalDate dateOfBirth;
    private final String gender;
    private final String phoneOwnerType;
    private final String address;
    private final String nationalId;
    private final String identityDocumentType;
    private final boolean hasAccount;
    private final long version;

    public PatientProfile(UUID id, String fullName, String phoneCanonical, LocalDate dateOfBirth, String gender) {
        this(id, fullName, phoneCanonical, dateOfBirth, gender, null, null, null, null, false, 0);
    }

    public PatientProfile(UUID id, String fullName, String phoneCanonical, LocalDate dateOfBirth, String gender,
            String phoneOwnerType, String address, String nationalId, String identityDocumentType, boolean hasAccount, long version) {
        this.id = id;
        this.fullName = fullName;
        this.phoneCanonical = phoneCanonical;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.phoneOwnerType = phoneOwnerType;
        this.address = address;
        this.nationalId = nationalId;
        this.identityDocumentType = identityDocumentType;
        this.hasAccount = hasAccount;
        this.version = version;
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
    public String phoneOwnerType() { return phoneOwnerType; }
    public String address() { return address; }
    public String nationalId() { return nationalId; }
    public String identityDocumentType() { return identityDocumentType; }
    public boolean hasAccount() { return hasAccount; }
    public long version() { return version; }
}
