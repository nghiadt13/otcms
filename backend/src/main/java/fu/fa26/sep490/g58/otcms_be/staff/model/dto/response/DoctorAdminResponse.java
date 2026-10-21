package fu.fa26.sep490.g58.otcms_be.staff.model.dto.response;

import java.util.UUID;

public class DoctorAdminResponse {
    private final UUID doctorProfileId;
    private final UUID accountId;
    private final String fullName;
    private final String photoUrl;
    private final String workContact;
    private final String specialties;
    private final String qualifications;
    private final String biography;
    private final String consultationInformation;
    private final boolean publiclyVisible;
    private final boolean acceptingNewBookings;
    private final boolean inActiveDirectory;
    public DoctorAdminResponse(UUID doctorProfileId, UUID accountId, String fullName, String photoUrl, String workContact, String specialties, String qualifications, String biography, String consultationInformation, boolean publiclyVisible, boolean acceptingNewBookings, boolean inActiveDirectory) {
        this.doctorProfileId = doctorProfileId;
        this.accountId = accountId;
        this.fullName = fullName;
        this.photoUrl = photoUrl;
        this.workContact = workContact;
        this.specialties = specialties;
        this.qualifications = qualifications;
        this.biography = biography;
        this.consultationInformation = consultationInformation;
        this.publiclyVisible = publiclyVisible;
        this.acceptingNewBookings = acceptingNewBookings;
        this.inActiveDirectory = inActiveDirectory;
    }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public UUID doctorProfileId() { return doctorProfileId; }
    public UUID getAccountId() { return accountId; }
    public UUID accountId() { return accountId; }
    public String getFullName() { return fullName; }
    public String fullName() { return fullName; }
    public String getPhotoUrl() { return photoUrl; }
    public String photoUrl() { return photoUrl; }
    public String getWorkContact() { return workContact; }
    public String workContact() { return workContact; }
    public String getSpecialties() { return specialties; }
    public String specialties() { return specialties; }
    public String getQualifications() { return qualifications; }
    public String qualifications() { return qualifications; }
    public String getBiography() { return biography; }
    public String biography() { return biography; }
    public String getConsultationInformation() { return consultationInformation; }
    public String consultationInformation() { return consultationInformation; }
    public boolean getPubliclyVisible() { return publiclyVisible; }
    public boolean isPubliclyVisible() { return publiclyVisible; }
    public boolean publiclyVisible() { return publiclyVisible; }
    public boolean getAcceptingNewBookings() { return acceptingNewBookings; }
    public boolean isAcceptingNewBookings() { return acceptingNewBookings; }
    public boolean acceptingNewBookings() { return acceptingNewBookings; }
    public boolean getInActiveDirectory() { return inActiveDirectory; }
    public boolean isInActiveDirectory() { return inActiveDirectory; }
    public boolean inActiveDirectory() { return inActiveDirectory; }
}
