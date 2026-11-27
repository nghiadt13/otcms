package fu.fa26.sep490.g58.otcms_be.auth.service;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;
import java.util.Optional;

/** Public persistence use case for the auth-owned profile table. */
    public interface PatientProfileDirectory {
    /** Internal owner projection; caller must authorize the requested profiles. */
    java.util.List<ProfileRecord> findProfiles(java.util.Collection<UUID> profileIds);
    Page<ProfileRecord> search(String fullName, String canonicalPhone, Pageable pageable);
    ProfileRecord create(ProfileRecord profile, String phoneOwnerType);

    Optional<UUID> findProfileAccount(UUID profileId);
    Optional<UUID> findPatientProfile(UUID accountId);
    Optional<ProfileRecord> findProfile(UUID profileId);
    java.util.List<ProfileRecord> match(String phone, LocalDate dateOfBirth, String normalizedFullName);
    java.util.List<ProfileRecord> matchDependent(String normalizedName, LocalDate dateOfBirth, String nationalId);
    boolean duplicateExists(String phone, LocalDate dateOfBirth, String normalizedFullName);
    ProfileRecord update(ProfileRecord profile, long expectedVersion);

    /**
     * BR-15: automatically links an unlinked profile whose email is marked as the patient's own
     * when the account has no own profile yet and exactly one unlinked profile matches.
     */
    default Optional<UUID> autoLinkUnlinkedOwnProfile(UUID accountId, String normalizedEmail) {
        return Optional.empty();
    }

        static class ProfileRecord {
    private final UUID id;
    private final UUID accountId;
    private final String fullName;
    private final String patientCode;
    private final String phoneCanonical;
    private final String phoneOwnerType;
    private final LocalDate dateOfBirth;
    private final String gender;
    private final String address;
    private final String nationalId;
    private final String identityDocumentType;
    private final String email;
    private final String emailOwnerType;
    private final long version;
    public ProfileRecord(UUID id, UUID accountId, String fullName, String phoneCanonical, String phoneOwnerType, LocalDate dateOfBirth, String gender) {
        this(id, accountId, fullName, phoneCanonical, phoneOwnerType, dateOfBirth, gender, null, null, null, null, null, 0);
    }
    public ProfileRecord(UUID id, UUID accountId, String fullName, String phoneCanonical, String phoneOwnerType,
            LocalDate dateOfBirth, String gender, String address, String nationalId, String identityDocumentType, long version) {
        this(id, accountId, fullName, phoneCanonical, phoneOwnerType, dateOfBirth, gender,
                address, nationalId, identityDocumentType, null, null, version, null);
    }
    public ProfileRecord(UUID id, UUID accountId, String fullName, String phoneCanonical, String phoneOwnerType,
            LocalDate dateOfBirth, String gender, String address, String nationalId, String identityDocumentType,
            long version, String patientCode) {
        this(id, accountId, fullName, phoneCanonical, phoneOwnerType, dateOfBirth, gender,
                address, nationalId, identityDocumentType, null, null, version, patientCode);
    }
    public ProfileRecord(UUID id, UUID accountId, String fullName, String phoneCanonical, String phoneOwnerType,
            LocalDate dateOfBirth, String gender, String address, String nationalId, String identityDocumentType,
            String email, String emailOwnerType, long version) {
        this(id, accountId, fullName, phoneCanonical, phoneOwnerType, dateOfBirth, gender,
                address, nationalId, identityDocumentType, email, emailOwnerType, version, null);
    }
    public ProfileRecord(UUID id, UUID accountId, String fullName, String phoneCanonical, String phoneOwnerType,
            LocalDate dateOfBirth, String gender, String address, String nationalId, String identityDocumentType,
            String email, String emailOwnerType, long version, String patientCode) {
        this.id = id;
        this.accountId = accountId;
        this.fullName = fullName;
        this.patientCode = patientCode;
        this.phoneCanonical = phoneCanonical;
        this.phoneOwnerType = phoneOwnerType;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.address = address;
        this.nationalId = nationalId;
        this.identityDocumentType = identityDocumentType;
        this.email = email;
        this.emailOwnerType = emailOwnerType;
        this.version = version;
    }
    public UUID getId() { return id; }
    public UUID id() { return id; }
    public UUID getAccountId() { return accountId; }
    public UUID accountId() { return accountId; }
    public String getFullName() { return fullName; }
    public String fullName() { return fullName; }
    public String patientCode() { return patientCode; }
    public String getPhoneCanonical() { return phoneCanonical; }
    public String phoneCanonical() { return phoneCanonical; }
    public String getPhoneOwnerType() { return phoneOwnerType; }
    public String phoneOwnerType() { return phoneOwnerType; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public LocalDate dateOfBirth() { return dateOfBirth; }
    public String getGender() { return gender; }
    public String gender() { return gender; }
    public String address() { return address; }
    public String nationalId() { return nationalId; }
    public String identityDocumentType() { return identityDocumentType; }
    public String email() { return email; }
    public String emailOwnerType() { return emailOwnerType; }
    public long version() { return version; }
}
}
