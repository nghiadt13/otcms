package fu.fa26.sep490.g58.otcms_be.auth.service.impl;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.Gender;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.PatientProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.PhoneOwnerType;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.IdentityDocumentType;
import java.util.List;
import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import fu.fa26.sep490.g58.otcms_be.auth.repository.PatientProfileRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientProfileDirectoryServiceImpl implements PatientProfileDirectory {
    private final PatientProfileRepository profiles;

    public PatientProfileDirectoryServiceImpl(PatientProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProfileRecord> search(String fullName, String canonicalPhone, Pageable pageable) {
        var matches = canonicalPhone == null
                ? profiles.findByFullNameContainingIgnoreCase(fullName, pageable)
                : profiles.findByFullNameContainingIgnoreCaseOrPhoneCanonicalContaining(fullName, canonicalPhone, pageable);
        return matches.map(this::toRecord);
    }

    @Override
    @Transactional
    public ProfileRecord create(ProfileRecord record, String phoneOwnerType) {
        PatientProfileJpaEntity entity = new PatientProfileJpaEntity();
        if (record.accountId() != null) entity.setId(record.id());
        entity.setAccountId(record.accountId());
        entity.setFullName(record.fullName());
        entity.setPhoneCanonical(record.phoneCanonical());
        entity.setPhoneOwnerType(phoneOwnerType == null ? null : PhoneOwnerType.valueOf(phoneOwnerType));
        entity.setDateOfBirth(record.dateOfBirth());
        entity.setGender(Gender.valueOf(record.gender()));
        entity.setAddress(record.address());
        entity.setNationalId(record.nationalId());
        entity.setIdentityDocumentType(record.identityDocumentType() == null ? null
                : IdentityDocumentType.valueOf(record.identityDocumentType()));
        return toRecord(profiles.save(entity));
    }

    @Override
    public Optional<UUID> findProfileAccount(UUID profileId) {
        return profiles.findByIdAndAccountIdIsNotNull(profileId).map(PatientProfileJpaEntity::getAccountId);
    }
    @Override
    public Optional<UUID> findPatientProfile(UUID accountId) {
        return profiles.findByAccountId(accountId).map(PatientProfileJpaEntity::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfileRecord> findProfile(UUID profileId) {
        return profiles.findById(profileId).map(this::toRecord);
    }

    private ProfileRecord toRecord(PatientProfileJpaEntity entity) {
        return new ProfileRecord(entity.getId(), entity.getAccountId(), entity.getFullName(), entity.getPhoneCanonical(),
                entity.getPhoneOwnerType() == null ? null : entity.getPhoneOwnerType().name(), entity.getDateOfBirth(), entity.getGender().name(),
                entity.getAddress(), entity.getNationalId(),
                entity.getIdentityDocumentType() == null ? null : entity.getIdentityDocumentType().name(), entity.getVersion());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfileRecord> match(String phone, LocalDate dob, String normalizedFullName) {
        var limit = PageRequest.of(0, 2);
        var result = normalizedFullName == null
                ? profiles.findByPhoneCanonicalAndDateOfBirth(phone, dob, limit)
                : profiles.findExactMatches(phone, dob, normalizedFullName, limit);
        return result.stream().map(this::toRecord).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfileRecord> matchDependent(String normalizedName, LocalDate dob, String nationalId) {
        return profiles.findDependentMatches(normalizedName, dob, nationalId, PageRequest.of(0, 2))
                .stream().map(this::toRecord).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean duplicateExists(String phone, LocalDate dob, String normalizedFullName) {
        return !profiles.findExactMatches(phone, dob, normalizedFullName, PageRequest.of(0, 1)).isEmpty();
    }

    @Override
    @Transactional
    public ProfileRecord update(ProfileRecord record, long expectedVersion) {
        var entity = profiles.findById(record.id()).orElseThrow(() ->
                new IllegalStateException("Patient profile disappeared during managed update"));
        if (entity.getVersion() != expectedVersion)
            throw new ObjectOptimisticLockingFailureException(PatientProfileJpaEntity.class, record.id());
        entity.setFullName(record.fullName());
        entity.setPhoneCanonical(record.phoneCanonical());
        entity.setPhoneOwnerType(record.phoneOwnerType() == null ? null : PhoneOwnerType.valueOf(record.phoneOwnerType()));
        entity.setDateOfBirth(record.dateOfBirth());
        entity.setGender(Gender.valueOf(record.gender()));
        entity.setAddress(record.address());
        entity.setNationalId(record.nationalId());
        entity.setIdentityDocumentType(record.identityDocumentType() == null ? null
                : IdentityDocumentType.valueOf(record.identityDocumentType()));
        // Keep account linkage and the managed version untouched. Hibernate guards the UPDATE with its version.
        profiles.flush();
        return toRecord(entity);
    }
}
