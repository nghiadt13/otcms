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
                profile.dateOfBirth(), profile.gender());
    }

    private String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value.trim().replaceAll("\\s+", " "), Normalizer.Form.NFC);
    }

    private String canonicalPhone(String raw) {
        if (raw == null) {
            return null;
        }
        String value = normalizePhonePrefix(raw);
        String digits = value.substring(1);
        return digits.matches("[0-9]{8,15}") ? "+" + digits : null;
    }

    private String phoneSearchTerm(String raw) {
        if (raw == null) {
            return null;
        }
        String value = normalizePhonePrefix(raw);
        String digits = value.substring(1);
        return digits.matches("[0-9]{1,15}") ? "+" + digits : null;
    }

    private String normalizePhonePrefix(String raw) {
        String value = raw.trim().replaceAll("[\\s().-]", "");
        if (value.startsWith("00")) {
            value = "+" + value.substring(2);
        }
        if (value.startsWith("0")) {
            value = "+84" + value.substring(1);
        }
        if (!value.startsWith("+")) {
            value = "+" + value;
        }
        return value;
    }
}
