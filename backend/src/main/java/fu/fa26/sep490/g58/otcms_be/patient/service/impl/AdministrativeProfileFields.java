package fu.fa26.sep490.g58.otcms_be.patient.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory.ProfileRecord;
import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse.FieldError;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientProfileException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Shared KAN-24 administrative normalization; contact policy is selected by the owning use case. */
public final class AdministrativeProfileFields {
    private AdministrativeProfileFields() { }
    private static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "OTHER");
    private static final Set<String> PHONE_OWNERS = Set.of("PATIENT", "GUARDIAN");
    private static final Set<String> EMAIL_OWNERS = Set.of("PATIENT", "RELATIVE");
    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public static ProfileRecord record(UUID id, UUID accountId, String rawName, String rawPhone, String rawOwner,
            LocalDate dob, String rawGender, String rawAddress, String rawNationalId, String rawDocumentType,
            long version, boolean creating, boolean nullableContact) {
        return record(id, accountId, rawName, rawPhone, rawOwner, dob, rawGender, rawAddress, rawNationalId,
                rawDocumentType, null, null, version, creating, nullableContact);
    }

    public static ProfileRecord record(UUID id, UUID accountId, String rawName, String rawPhone, String rawOwner,
            LocalDate dob, String rawGender, String rawAddress, String rawNationalId, String rawDocumentType,
            String rawEmail, String rawEmailOwner, long version, boolean creating, boolean nullableContact) {
        String name = normalizeName(rawName);
        String phone = canonicalPhone(rawPhone);
        String owner = rawOwner == null ? "" : rawOwner.trim().toUpperCase(Locale.ROOT);
        String gender = rawGender == null ? "" : rawGender.trim().toUpperCase(Locale.ROOT);
        String address = rawAddress == null ? null : rawAddress.trim();
        String nationalId = optionalText(rawNationalId);
        String documentType = optionalText(rawDocumentType);
        String email = normalizeEmail(rawEmail);
        String emailOwner = optionalText(rawEmailOwner);
        if (emailOwner != null) emailOwner = emailOwner.toUpperCase(Locale.ROOT);
        var errors = new ArrayList<FieldError>();
        if (name.length() < 2 || name.length() > 100) errors.add(field("fullName"));
        if (phone == null && (!nullableContact || rawPhone != null && !rawPhone.isBlank())) errors.add(field("phone"));
        if (!PHONE_OWNERS.contains(owner) && !(nullableContact && owner.isEmpty())) errors.add(field("phoneOwnerType"));
        if (dob == null || dob.isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))) errors.add(field("dateOfBirth"));
        if (!GENDERS.contains(gender)) errors.add(field("gender"));
        if (creating && (address == null || address.isBlank())) errors.add(field("address"));
        if (nationalId != null && nationalId.length() > 20) errors.add(field("nationalId"));
        if ((nationalId != null && documentType == null) || (documentType != null
                && !Set.of("CCCD", "PERSONAL_ID", "PASSPORT").contains(documentType)))
            errors.add(field("identityDocumentType"));
        if (email != null && (email.length() > 255 || !EMAIL_PATTERN.matcher(email).matches()))
            errors.add(field("email"));
        if ((email != null && emailOwner == null) || (email == null && emailOwner != null)
                || (emailOwner != null && !EMAIL_OWNERS.contains(emailOwner)))
            errors.add(field("emailOwnerType"));
        if (!errors.isEmpty()) throw new PatientProfileException("VALIDATION_FAILED", errors);
        return new ProfileRecord(id, accountId, name, phone, owner.isEmpty() ? null : owner, dob, gender, address,
                nationalId, documentType, email, emailOwner, version);
    }

    public static String normalizeEmail(String raw) {
        String text = optionalText(raw);
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    public static String optionalText(String text) { return text == null || text.isBlank() ? null : text.trim(); }
    private static FieldError field(String name) { return new FieldError(name, "MSG-17"); }
    private static PatientProfileException invalid(String name) {
        return new PatientProfileException("VALIDATION_FAILED", List.of(field(name)));
    }

    public static String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value.trim().replaceAll("\\s+", " "), Normalizer.Form.NFC);
    }

    public static String canonicalPhone(String raw) {
        if (raw == null) {
            return null;
        }
        String value = normalizePhonePrefix(raw);
        String digits = value.substring(1);
        return digits.matches("[0-9]{8,15}") ? "+" + digits : null;
    }

    public static String phoneSearchTerm(String raw) {
        if (raw == null) {
            return null;
        }
        String value = normalizePhonePrefix(raw);
        String digits = value.substring(1);
        return digits.matches("[0-9]{1,15}") ? "+" + digits : null;
    }

    public static String normalizePhonePrefix(String raw) {
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
