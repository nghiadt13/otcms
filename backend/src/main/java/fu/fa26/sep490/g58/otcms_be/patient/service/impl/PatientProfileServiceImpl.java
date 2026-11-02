package fu.fa26.sep490.g58.otcms_be.patient.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory.ProfileRecord;
import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientProfileException;
import fu.fa26.sep490.g58.otcms_be.patient.model.PatientProfile;
import fu.fa26.sep490.g58.otcms_be.patient.model.ReceptionVerificationProfile;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientProfileService;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse.FieldError;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientProfileServiceImpl implements PatientProfileService {
    private static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "OTHER");
    private static final Set<String> PHONE_OWNERS = Set.of("PATIENT", "GUARDIAN");
    private final PatientProfileDirectory profiles;
    private final CoordinationGuardService coordinationGuard;
    private final ReceptionistAccountAccessService receptionists;

    public PatientProfileServiceImpl(PatientProfileDirectory profiles, CoordinationGuardService coordinationGuard,
            ReceptionistAccountAccessService receptionists) {
        this.profiles = profiles;
        this.coordinationGuard = coordinationGuard;
        this.receptionists = receptionists;
    }

    @Transactional(readOnly = true)
    public Page<PatientProfile> search(String rawQuery, int page, int size) {
        String query = normalizeName(rawQuery);
        if (query.isBlank() || query.length() > 100 || page < 0 || size < 1 || size > 50) {
            throw new PatientProfileException("VALIDATION_FAILED");
        }
        var result = profiles.search(query, phoneSearchTerm(rawQuery), PageRequest.of(page, size));
        List<PatientProfile> content = result.getContent().stream().map(this::toModel).toList();
        return new PageImpl<>(content, PageRequest.of(page, size), result.getTotalElements());
    }

    @Transactional
    public PatientProfile create(String rawName, String rawPhone, String phoneOwnerType,
                                 LocalDate dateOfBirth, String gender) {
        String name = normalizeName(rawName);
        String phone = canonicalPhone(rawPhone);
        String owner = phoneOwnerType == null ? "" : phoneOwnerType.trim().toUpperCase(Locale.ROOT);
        String normalizedGender = gender == null ? "" : gender.trim().toUpperCase(Locale.ROOT);
        if (name.length() < 2 || name.length() > 100 || phone == null || dateOfBirth == null
                || dateOfBirth.isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))
                || !PHONE_OWNERS.contains(owner) || !GENDERS.contains(normalizedGender)) {
            throw new PatientProfileException("VALIDATION_FAILED");
        }
        coordinationGuard.acquire();
        PatientProfile profile = new PatientProfile(UUID.randomUUID(), name, phone, dateOfBirth, normalizedGender);
        ProfileRecord record = new ProfileRecord(profile.id(), null, profile.fullName(), profile.phoneCanonical(),
                owner, profile.dateOfBirth(), profile.gender());
        return toModel(profiles.create(record, owner));
    }

    /** @ears REQ-RECEPTION-002 AC25 AC31 */
    @Override
    @Transactional(readOnly = true)
    public ReceptionVerificationProfile getReceptionVerificationProfile(UUID actorAccountId, UUID patientProfileId) {
        if (!receptionists.isActiveReceptionistAccount(actorAccountId)) {
            throw new PatientProfileException("FORBIDDEN");
        }
        ProfileRecord profile = profiles.findProfile(patientProfileId)
                .orElseThrow(() -> new PatientProfileException("PATIENT_PROFILE_NOT_FOUND"));
        return new ReceptionVerificationProfile(profile.id(), profile.fullName(), profile.dateOfBirth());
    }

    private PatientProfile toModel(ProfileRecord profile) {
        return new PatientProfile(profile.id(), profile.fullName(), profile.phoneCanonical(),
                profile.dateOfBirth(), profile.gender(), profile.phoneOwnerType(), profile.address(), profile.nationalId(),
                profile.identityDocumentType(), profile.accountId() != null, profile.version());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientProfile> matches(UUID actorAccountId, String rawPhone, LocalDate dob, String rawName) {
        requireActiveReceptionist(actorAccountId);
        String phone = canonicalPhone(rawPhone);
        if (phone == null) throw invalid("phone");
        if (dob == null || dob.isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))) throw invalid("dateOfBirth");
        var candidates = profiles.match(phone, dob, null);
        if (candidates.size() > 1) {
            String name = normalizeName(rawName);
            if (name.isBlank()) throw new PatientProfileException("PATIENT_MATCH_AMBIGUOUS");
            if (name.length() < 2 || name.length() > 100) throw invalid("fullName");
            candidates = profiles.match(phone, dob, name);
            if (candidates.size() > 1) throw new PatientProfileException("PATIENT_MATCH_AMBIGUOUS");
        }
        return candidates.stream().map(this::toModel).toList();
    }

    @Override
    @Transactional
    public PatientProfile create(UUID actorAccountId, String name, String phone, String owner, LocalDate dob,
            String gender, String address, String nationalId, String documentType) {
        requireActiveReceptionist(actorAccountId);
        ProfileRecord record = administrativeRecord(UUID.randomUUID(), null, name, phone, owner, dob, gender,
                address, nationalId, documentType, 0, true);
        coordinationGuard.acquire();
        if (profiles.duplicateExists(record.phoneCanonical(), record.dateOfBirth(), record.fullName()))
            throw new PatientProfileException("PATIENT_PROFILE_DUPLICATE");
        return toModel(profiles.create(record, record.phoneOwnerType()));
    }

    @Override
    @Transactional(readOnly = true)
    public PatientProfile detail(UUID actorAccountId, UUID patientProfileId) {
        requireActiveReceptionist(actorAccountId);
        return toModel(requiredProfile(patientProfileId));
    }

    @Override
    @Transactional
    public PatientProfile update(UUID actorAccountId, UUID patientProfileId, String name, String phone, String owner,
            LocalDate dob, String gender, String address, String nationalId, String documentType, Long version) {
        requireActiveReceptionist(actorAccountId);
        if (version == null || version < 0) throw invalid("version");
        ProfileRecord existing = requiredProfile(patientProfileId);
        if (existing.version() != version) throw new PatientProfileException("STALE_VERSION");
        ProfileRecord record = administrativeRecord(patientProfileId, existing.accountId(), name, phone, owner, dob,
                gender, address, nationalId, documentType, version, false);
        return toModel(profiles.update(record, version));
    }

    private ProfileRecord requiredProfile(UUID id) {
        return profiles.findProfile(id).orElseThrow(() -> new PatientProfileException("PATIENT_PROFILE_NOT_FOUND"));
    }

    private void requireActiveReceptionist(UUID actor) {
        if (!receptionists.isActiveReceptionistAccount(actor)) throw new PatientProfileException("ACCESS_DENIED");
    }

    private ProfileRecord administrativeRecord(UUID id, UUID accountId, String name, String phone, String owner,
            LocalDate dob, String gender, String address, String nationalId, String type, long version, boolean creating) {
        return AdministrativeProfileFields.record(id, accountId, name, phone, owner, dob, gender, address,
                nationalId, type, version, creating, false);
    }

    private String normalizeName(String value) { return AdministrativeProfileFields.normalizeName(value); }
    private String canonicalPhone(String value) { return AdministrativeProfileFields.canonicalPhone(value); }
    private String phoneSearchTerm(String value) { return AdministrativeProfileFields.phoneSearchTerm(value); }
    private PatientProfileException invalid(String name) {
        return new PatientProfileException("VALIDATION_FAILED", List.of(new FieldError(name, "MSG-17")));
    }
}
