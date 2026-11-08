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

        static class ProfileRecord {
    private final UUID id;
    private final UUID accountId;
    private final String fullName;
    private final String phoneCanonical;
    private final String phoneOwnerType;
    private final LocalDate dateOfBirth;
    private final String gender;
    private final String address;
    private final String nationalId;
    private final String identityDocumentType;
    private final long version;
    public ProfileRecord(UUID id, UUID accountId, String fullName, String phoneCanonical, String phoneOwnerType, LocalDate dateOfBirth, String gender) {
        this(id, accountId, fullName, phoneCanonical, phoneOwnerType, dateOfBirth, gender, null, null, null, 0);
    }
    public ProfileRecord(UUID id, UUID accountId, String fullName, String phoneCanonical, String phoneOwnerType,
            LocalDate dateOfBirth, String gender, String address, String nationalId, String identityDocumentType, long version) {
        this.id = id;
        this.accountId = accountId;
        this.fullName = fullName;
        this.phoneCanonical = phoneCanonical;
        this.phoneOwnerType = phoneOwnerType;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.address = address;
        this.nationalId = nationalId;
        this.identityDocumentType = identityDocumentType;
        this.version = version;
    }
    public UUID getId() { return id; }
    public UUID id() { return id; }
    public UUID getAccountId() { return accountId; }
    public UUID accountId() { return accountId; }
    public String getFullName() { return fullName; }
    public String fullName() { return fullName; }
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
    public long version() { return version; }
}
}
