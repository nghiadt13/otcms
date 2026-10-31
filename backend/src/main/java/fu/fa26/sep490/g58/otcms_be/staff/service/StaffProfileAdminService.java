package fu.fa26.sep490.g58.otcms_be.staff.service;

import fu.fa26.sep490.g58.otcms_be.auth.service.StaffAccountDirectory;
import fu.fa26.sep490.g58.otcms_be.appointment.service.AppointmentMutationImpactService;
import fu.fa26.sep490.g58.otcms_be.staff.exception.StaffProfileException;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.request.StaffProfileInput;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.StaffProfileResponse;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.StaffProfileResponse.DoctorInfo;
import fu.fa26.sep490.g58.otcms_be.staff.model.dto.response.StaffProfilesPageResponse;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.StaffProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.DoctorProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.staff.repository.StaffProfileRepository;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorProfileRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

@Service
public class StaffProfileAdminService {
    private final StaffProfileRepository profiles;
    private final DoctorProfileRepository doctors;
    private final StaffAccountDirectory accounts;
    private final AppointmentMutationImpactService appointments;

    public StaffProfileAdminService(StaffProfileRepository profiles, DoctorProfileRepository doctors,
            StaffAccountDirectory accounts, AppointmentMutationImpactService appointments) {
        this.profiles = profiles; this.doctors = doctors; this.accounts = accounts;
        this.appointments = appointments;
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
        validatePhoto(photo, photoType, profile);
        profiles.saveAndFlush(profile);
        if (account.role().name().equals("DOCTOR")) {
            DoctorInfo info = input.doctor();
            doctors.saveAndFlush(new DoctorProfileJpaEntity(id, account.id(), profile.displayName(),
                    profile.photoContent() == null ? null : photoPath(id),
                    profile.workContact(), String.join(",", info.specialties()), info.qualifications(),
                    info.biography(), info.consultationInfo(), info.isPublic(), false, true));
        }
        return response(profile);
    }

    @Transactional
    public StaffProfileResponse update(UUID id, StaffProfileInput input, byte[] photo, String photoType) {
        StaffProfileJpaEntity profile = findActive(id);
        var account = accounts.staff(profile.accountId()).orElseThrow(() ->
                new StaffProfileException("STAFF_ACCOUNT_UNAVAILABLE"));
        validate(input, account.role().name());
        profile.update(input.displayName().trim(), input.workContact().trim());
        validatePhoto(photo, photoType, profile);
        if (account.role().name().equals("DOCTOR")) {
            DoctorProfileJpaEntity doctor = doctors.findById(id).orElseThrow(() ->
                    new StaffProfileException("STAFF_PROFILE_NOT_FOUND"));
            DoctorInfo info = input.doctor();
            doctor.updateProfile(profile.displayName(), profile.workContact(), String.join(",", info.specialties()),
                    info.qualifications(), info.biography(), info.consultationInfo(), info.isPublic());
            if (profile.photoContent() != null) doctor.updatePhotoUrl(photoPath(id));
            doctors.save(doctor);
        }
        return response(profiles.saveAndFlush(profile));
    }

    @Transactional
    public void remove(UUID id) {
        StaffProfileJpaEntity profile = findActive(id);
        if (doctors.existsById(id) && appointments.findProtectedBookingIntervals(id).stream()
                .anyMatch(interval -> interval.occupiedUntil().isAfter(Instant.now())))
            throw new StaffProfileException("DOCTOR_HAS_FUTURE_APPOINTMENTS");
        doctors.findById(id).ifPresent(doctor -> {
            doctor.updateDirectoryState(false, false, false);
            doctors.save(doctor);
        });
        profile.remove();
        profiles.save(profile);
    }

    @Transactional
    public StaffProfileResponse savePhoto(UUID id, byte[] bytes, String type) {
        StaffProfileJpaEntity profile = findActive(id);
        validatePhoto(bytes, type, profile);
        doctors.findById(id).ifPresent(doctor -> {
            doctor.updatePhotoUrl(photoPath(id));
            doctors.save(doctor);
        });
        return response(profiles.saveAndFlush(profile));
    }

    private void validatePhoto(byte[] bytes, String type, StaffProfileJpaEntity profile) {
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
        profile.photo(bytes, type);
    }

    @Transactional(readOnly = true)
    public StaffProfileJpaEntity photo(UUID id, boolean admin) {
        StaffProfileJpaEntity profile = find(id);
        if (!"ACTIVE".equals(profile.status()) || profile.photoContent() == null)
            throw new StaffProfileException("STAFF_PROFILE_NOT_FOUND");
        if (!admin && doctors.findById(id)
                .filter(doctor -> doctor.publiclyVisible() && doctor.inActiveDirectory()).isEmpty())
            throw new AccessDeniedException("Staff photo is private");
        return profile;
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
        DoctorProfileJpaEntity doctor = doctors.findById(profile.id()).orElse(null);
        DoctorInfo info = doctor == null ? null : new DoctorInfo(
                doctor.specialties().isBlank() ? List.of() : List.of(doctor.specialties().split(",")),
                doctor.qualifications(), doctor.biography(), doctor.consultationInformation(), doctor.publiclyVisible());
        boolean active = profile.status().equals("ACTIVE");
        String role = account.role().name().equals("DOCTOR") ? "ORTHOPEDIC_DOCTOR" : account.role().name();
        String photoUrl = profile.photoContent() == null
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
