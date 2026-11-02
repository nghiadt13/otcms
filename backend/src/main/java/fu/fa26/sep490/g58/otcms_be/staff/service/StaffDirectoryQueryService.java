package fu.fa26.sep490.g58.otcms_be.staff.service;

import java.time.Instant;
import java.util.UUID;

/** Staff-owned identity and current booking-eligibility projection. */
    public interface StaffDirectoryQueryService {
    DoctorBookingProfile findDoctorByAccount(UUID accountId, Instant requestedStart, Instant requestedEnd);

        static class DoctorBookingProfile {
    private final UUID doctorProfileId;
    private final boolean publiclyVisible;
    private final boolean acceptingNewBookings;
    private final boolean inActiveDirectory;
    private final boolean coveredByWorkShift;
    private final boolean coveredByOperationalTimeOff;
    public DoctorBookingProfile(UUID doctorProfileId, boolean publiclyVisible, boolean acceptingNewBookings, boolean inActiveDirectory, boolean coveredByWorkShift, boolean coveredByOperationalTimeOff) {
        this.doctorProfileId = doctorProfileId;
        this.publiclyVisible = publiclyVisible;
        this.acceptingNewBookings = acceptingNewBookings;
        this.inActiveDirectory = inActiveDirectory;
        this.coveredByWorkShift = coveredByWorkShift;
        this.coveredByOperationalTimeOff = coveredByOperationalTimeOff;
    }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public UUID doctorProfileId() { return doctorProfileId; }
    public boolean getPubliclyVisible() { return publiclyVisible; }
    public boolean isPubliclyVisible() { return publiclyVisible; }
    public boolean publiclyVisible() { return publiclyVisible; }
    public boolean getAcceptingNewBookings() { return acceptingNewBookings; }
    public boolean isAcceptingNewBookings() { return acceptingNewBookings; }
    public boolean acceptingNewBookings() { return acceptingNewBookings; }
    public boolean getInActiveDirectory() { return inActiveDirectory; }
    public boolean isInActiveDirectory() { return inActiveDirectory; }
    public boolean inActiveDirectory() { return inActiveDirectory; }
    public boolean getCoveredByWorkShift() { return coveredByWorkShift; }
    public boolean isCoveredByWorkShift() { return coveredByWorkShift; }
    public boolean coveredByWorkShift() { return coveredByWorkShift; }
    public boolean getCoveredByOperationalTimeOff() { return coveredByOperationalTimeOff; }
    public boolean isCoveredByOperationalTimeOff() { return coveredByOperationalTimeOff; }
    public boolean coveredByOperationalTimeOff() { return coveredByOperationalTimeOff; }
}
}
