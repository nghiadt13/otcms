package fu.fa26.sep490.g58.otcms_be.staff.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Minimal Staff-owned projection of Doctors who are accepting bookings in the active directory. */
    public interface StaffAvailabilityProviderQueryService {
    List<AvailabilityProvider> findAcceptingDoctorsInActiveDirectory();

    /** Looks up a Doctor's name regardless of current booking eligibility. */
    Optional<String> findDoctorDisplayName(UUID doctorProfileId);

        static class AvailabilityProvider {
    private final UUID doctorProfileId;
    private final String displayName;
    private final boolean publiclyVisible;
    public AvailabilityProvider(UUID doctorProfileId, String displayName, boolean publiclyVisible) {
        this.doctorProfileId = doctorProfileId;
        this.displayName = displayName;
        this.publiclyVisible = publiclyVisible;
    }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public UUID doctorProfileId() { return doctorProfileId; }
    public String getDisplayName() { return displayName; }
    public String displayName() { return displayName; }
    public boolean getPubliclyVisible() { return publiclyVisible; }
    public boolean isPubliclyVisible() { return publiclyVisible; }
    public boolean publiclyVisible() { return publiclyVisible; }
}
}
