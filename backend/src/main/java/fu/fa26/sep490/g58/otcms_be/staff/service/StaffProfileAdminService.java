package fu.fa26.sep490.g58.otcms_be.staff.service;

import fu.fa26.sep490.g58.otcms_be.auth.service.StaffAccountDirectory;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentMutationImpactService;
import fu.fa26.sep490.g58.otcms_be.common.storage.ObjectStoragePort;
import fu.fa26.sep490.g58.otcms_be.staff.exception.StaffProfileException;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.request.StaffProfileInput;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.StaffProfileResponse;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.StaffProfileResponse.DoctorInfo;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.StaffProfilesPageResponse;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.StaffProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.staff.repository.StaffProfileRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.security.access.AccessDeniedException;

@Service
public class StaffProfileAdminService {
    private static final Logger log = LoggerFactory.getLogger(StaffProfileAdminService.class);
    private final StaffProfileRepository profiles;
    private final StaffAccountDirectory accounts;
    private final AppointmentMutationImpactService appointments;
    private final ObjectStoragePort storage;

    public StaffProfileAdminService(StaffProfileRepository profiles,
            StaffAccountDirectory accounts, AppointmentMutationImpactService appointments,
            ObjectStoragePort storage) {
        this.profiles = profiles; this.accounts = accounts;
        this.appointments = appointments; this.storage = storage;
    }

    @Transactional(readOnly = true)
    public StaffProfilesPageResponse list(String q, String role, String status, Boolean isPublic, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw invalid();
        String term = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        if (term.length() > 100 || (status != null && !List.of("ACTIVE", "REMOVED").contains(status))) throw invalid();
        List<StaffProfileResponse> matched = profiles.findAll().stream().map(this::response)
                .filter(p -> term.isEmpty() || p.displayName().toLowerCase(Locale.ROOT).contains(term)
                        || p.username().toLowerCase(Locale.ROOT).contains(term))
                .filter(p -> role == null || role.isBlank() || p.role().equals(role))
                .filter(p -> status == null || status.isBlank() || p.status().equals(status))
                .filter(p -> isPublic == null || (p.isPublic() != null && p.isPublic().equals(isPublic)))
                .sorted(Comparator.comparing(StaffProfileResponse::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int start = (int) Math.min((long) page * size, matched.size());
        int end = Math.min(start + size, matched.size());
        return new StaffProfilesPageResponse(matched.subList(start, end), page, size,
                matched.size(), (matched.size() + size - 1) / size);
    }

    @Transactional(readOnly = true)
    public StaffProfileResponse detail(UUID id) { return response(find(id)); }

    @Transactional(readOnly = true)
    public List<StaffAccountDirectory.Entry> eligible() {
        return accounts.activeStaff().stream().filter(a -> !profiles.existsByAccountId(a.id())).toList();
    }

    @Transactional
    public StaffProfileResponse create(StaffProfileInput input, byte[] photo, String photoType) {
        if (input == null || input.accountId() == null) throw invalid();
        var account = accounts.activeStaff(input.accountId()).orElseThrow(() ->
                new StaffProfileException("STAFF_ACCOUNT_UNAVAILABLE"));
        if (profiles.existsByAccountId(account.id())) throw new StaffProfileException("STAFF_PROFILE_CONFLICT");
        validate(input, account.role().name());
        UUID id = UUID.randomUUID();
        StaffProfileJpaEntity profile = new StaffProfileJpaEntity(id, account.id(),
                input.displayName().trim(), input.workContact().trim());
        validatePhoto(photo, photoType);
        // Object first; the row points at it in the same transaction and the object goes away if that fails.
        String newKey = photo == null ? null : uploadPhoto(id, photo, photoType);
        try {
            if (newKey != null) profile.photo(newKey, photoType);
            if (account.role().name().equals("DOCTOR")) {
                DoctorInfo info = input.doctor();
                profile.becomeDoctor(String.join(",", info.specialties()), info.qualifications(),
                        info.biography(), info.consultationInfo(), info.isPublic());
                if (profile.photoStorageKey() != null) profile.updatePhotoUrl(photoPath(id));
            }
            profiles.saveAndFlush(profile);
            return response(profile);
        } catch (RuntimeException exception) {
            deleteQuietly(newKey);
            throw exception;
        }
    }

    @Transactional
    public StaffProfileResponse update(UUID id, StaffProfileInput input, byte[] photo, String photoType) {
        StaffProfileJpaEntity profile = findActive(id);
        var account = accounts.staff(profile.accountId()).orElseThrow(() ->
                new StaffProfileException("STAFF_ACCOUNT_UNAVAILABLE"));
        validate(input, account.role().name());
        profile.update(input.displayName().trim(), input.workContact().trim());
        validatePhoto(photo, photoType);
        String previousKey = profile.photoStorageKey();
        String newKey = photo == null ? null : uploadPhoto(id, photo, photoType);
        try {
            if (newKey != null) profile.photo(newKey, photoType);
            if (account.role().name().equals("DOCTOR")) {
                if (!profile.isDoctor()) throw new StaffProfileException("STAFF_PROFILE_NOT_FOUND");
                DoctorInfo info = input.doctor();
                profile.updateDoctorProfile(profile.displayName(), profile.workContact(),
                        String.join(",", info.specialties()), info.qualifications(), info.biography(),
                        info.consultationInfo(), info.isPublic());
                if (profile.photoStorageKey() != null) profile.updatePhotoUrl(photoPath(id));
            }
            profiles.saveAndFlush(profile);
            if (newKey != null) deleteAfterCommit(previousKey);
            return response(profile);
        } catch (RuntimeException exception) {
            deleteQuietly(newKey);
            throw exception;
        }
    }

    @Transactional
    public void remove(UUID id) {
        StaffProfileJpaEntity profile = findActive(id);
        if (profile.isDoctor() && appointments.findProtectedBookingIntervals(id).stream()
                .anyMatch(interval -> interval.occupiedUntil().isAfter(Instant.now())))
            throw new StaffProfileException("DOCTOR_HAS_FUTURE_APPOINTMENTS");
        if (profile.isDoctor()) profile.updateDirectoryState(false, false, false);
        profile.remove();
        profiles.save(profile);
    }

    @Transactional
    public StaffProfileResponse savePhoto(UUID id, byte[] bytes, String type) {
        StaffProfileJpaEntity profile = findActive(id);
        validatePhoto(bytes, type);
        String previousKey = profile.photoStorageKey();
        String newKey = bytes == null ? null : uploadPhoto(id, bytes, type);
        try {
            if (newKey != null) profile.photo(newKey, type);
            if (profile.isDoctor()) profile.updatePhotoUrl(photoPath(id));
            profiles.saveAndFlush(profile);
            if (newKey != null) deleteAfterCommit(previousKey);
            return response(profile);
        } catch (RuntimeException exception) {
            deleteQuietly(newKey);
            throw exception;
        }
    }

    private void validatePhoto(byte[] bytes, String type) {
        if (bytes == null) return;
        if (bytes.length == 0 || bytes.length > 2 * 1024 * 1024
                || !List.of("image/jpeg", "image/png", "image/webp").contains(type)) throw invalid();
        boolean signature = switch (type) {
            case "image/jpeg" -> bytes.length >= 3 && (bytes[0] & 255) == 0xFF
                    && (bytes[1] & 255) == 0xD8 && (bytes[2] & 255) == 0xFF;
            case "image/png" -> bytes.length >= 8 && (bytes[0] & 255) == 0x89
                    && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
            case "image/webp" -> bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                    && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W'
                    && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
        if (!signature) throw invalid();
    }

    /** Writes the photo under a fresh key; the caller stores that key on the row afterwards. */
    private String uploadPhoto(UUID staffProfileId, byte[] bytes, String type) {
        String key = "staff-photos/" + staffProfileId + "/" + UUID.randomUUID() + "." + extension(type);
        storage.put(key, type, bytes);
        return key;
    }

    private String extension(String type) {
        return switch (type) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw invalid();
        };
    }

    /** Best effort: an orphan object only wastes space, so a failed delete is logged, not thrown. */
    private void deleteQuietly(String key) {
        if (key == null) return;
        try {
            storage.delete(key);
        } catch (RuntimeException exception) {
            log.warn("Could not delete staff photo object {}", key, exception);
        }
    }

    /** Removes a replaced photo object once the new row is saved: after commit in a transaction, at once otherwise. */
    private void deleteAfterCommit(String key) {
        if (key == null) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { deleteQuietly(key); }
        });
    }

    /** Photo bytes read from object storage, with the content type kept on the profile row. */
    public record StaffPhoto(byte[] content, String contentType) { }

    /** Photo of an ACTIVE profile the caller may see; a missing key or missing object means no photo. */
    @Transactional(readOnly = true)
    public StaffPhoto photo(UUID id, boolean admin) {
        StaffProfileJpaEntity profile = find(id);
        if (!"ACTIVE".equals(profile.status()) || profile.photoStorageKey() == null)
            throw new StaffProfileException("STAFF_PROFILE_NOT_FOUND");
        if (!admin && !(profile.isDoctor() && profile.publiclyVisible() && profile.inActiveDirectory()))
            throw new AccessDeniedException("Staff photo is private");
        byte[] content = storage.get(profile.photoStorageKey()).orElseThrow(() ->
                new StaffProfileException("STAFF_PROFILE_NOT_FOUND"));
        return new StaffPhoto(content, profile.photoContentType());
    }

    private StaffProfileJpaEntity find(UUID id) {
        return profiles.findById(id).orElseThrow(() -> new StaffProfileException("STAFF_PROFILE_NOT_FOUND"));
    }
    private StaffProfileJpaEntity findActive(UUID id) {
        StaffProfileJpaEntity profile = find(id);
        if (!"ACTIVE".equals(profile.status())) throw new StaffProfileException("STAFF_PROFILE_CONFLICT");
        return profile;
    }
    private void validate(StaffProfileInput input, String role) {
        if (input == null || input.displayName() == null || input.displayName().isBlank()
                || input.displayName().trim().length() > 100 || input.workContact() == null
                || input.workContact().isBlank() || input.workContact().trim().length() > 100
                || !(input.workContact().trim().matches("[0-9+()\\s.\\-]{8,20}")
                    || input.workContact().trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
                || (role.equals("DOCTOR") && input.doctor() == null)
                || (!role.equals("DOCTOR") && input.doctor() != null)) throw invalid();
        if (role.equals("DOCTOR")) {
            DoctorInfo doctor = input.doctor();
            if (doctor.specialties() == null || doctor.specialties().stream().anyMatch(s -> s == null || s.contains(","))
                    || doctor.qualifications() == null || doctor.qualifications().length() > 200
                    || doctor.biography() == null || doctor.biography().length() > 2000
                    || doctor.consultationInfo() == null || doctor.consultationInfo().length() > 1000)
                throw invalid();
        }
    }
    private StaffProfileException invalid() { return new StaffProfileException("VALIDATION_FAILED"); }
    private String photoPath(UUID id) { return "/api/v1/staff-profiles/" + id + "/photo"; }
    private StaffProfileResponse response(StaffProfileJpaEntity profile) {
        var account = accounts.staff(profile.accountId()).orElseThrow(() ->
                new StaffProfileException("STAFF_ACCOUNT_UNAVAILABLE"));
        // Doctor fields live on the same row since final-db V110; non-doctors keep null doctor fields.
        StaffProfileJpaEntity doctor = profile.isDoctor() ? profile : null;
        DoctorInfo info = doctor == null ? null : new DoctorInfo(
                doctor.specialties().isBlank() ? List.of() : List.of(doctor.specialties().split(",")),
                doctor.qualifications(), doctor.biography(), doctor.consultationInformation(), doctor.publiclyVisible());
        boolean active = profile.status().equals("ACTIVE");
        String role = account.role().name().equals("DOCTOR") ? "ORTHOPEDIC_DOCTOR" : account.role().name();
        String photoUrl = profile.photoStorageKey() == null
                ? doctor == null ? null : doctor.photoUrl() : photoPath(profile.id());
        return new StaffProfileResponse(profile.id(), profile.accountId(), account.username(), role,
                profile.displayName(), photoUrl, profile.workContact(), profile.status(),
                doctor == null ? null : doctor.publiclyVisible(), active
                    ? doctor == null ? List.of("edit", "remove") : List.of("edit", "remove", "togglePublic")
                    : List.of(), info, active && doctor != null && doctor.publiclyVisible()
                    ? "/doctors/" + profile.id() : null,
                doctor == null ? null : doctor.acceptingNewBookings(),
                doctor == null ? null : doctor.inActiveDirectory());
    }
}
