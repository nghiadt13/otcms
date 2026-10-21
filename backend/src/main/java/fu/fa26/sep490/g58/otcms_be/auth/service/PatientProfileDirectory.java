package fu.fa26.sep490.g58.otcms_be.auth.service;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.UUID;
import java.util.Optional;

/** Public persistence use case for the auth-owned profile table. */
    public interface PatientProfileDirectory {
    Page<ProfileRecord> search(String fullName, String canonicalPhone, Pageable pageable);
    ProfileRecord create(ProfileRecord profile, String phoneOwnerType);

    Optional<UUID> findProfileAccount(UUID profileId);
    Optional<UUID> findPatientProfile(UUID accountId);

        static class ProfileRecord {
    private final UUID id;
    private final UUID accountId;
    private final String fullName;
    private final String phoneCanonical;
    private final String phoneOwnerType;
    private final LocalDate dateOfBirth;
    private final String gender;
    public ProfileRecord(UUID id, UUID accountId, String fullName, String phoneCanonical, String phoneOwnerType, LocalDate dateOfBirth, String gender) {
        this.id = id;
        this.accountId = accountId;
        this.fullName = fullName;
        this.phoneCanonical = phoneCanonical;
        this.phoneOwnerType = phoneOwnerType;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
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
}
}
