package fu.fa26.sep490.g58.otcms_be.auth.service.impl;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.Gender;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.PatientProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.PhoneOwnerType;
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
        entity.setPhoneOwnerType(PhoneOwnerType.valueOf(phoneOwnerType));
        entity.setDateOfBirth(record.dateOfBirth());
        entity.setGender(Gender.valueOf(record.gender()));
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
                entity.getPhoneOwnerType().name(), entity.getDateOfBirth(), entity.getGender().name());
    }
}
