package fu.fa26.sep490.g58.otcms_be.staff.model.dto.response;

import java.util.UUID;

public class DoctorPublicResponse {
    private final UUID doctorProfileId;
    private final String fullName;
    private final String photoUrl;
    private final String specialties;
    private final String qualifications;
    private final String biography;
    private final String consultationInformation;
    public DoctorPublicResponse(UUID doctorProfileId, String fullName, String photoUrl, String specialties, String qualifications, String biography, String consultationInformation) {
        this.doctorProfileId = doctorProfileId;
        this.fullName = fullName;
        this.photoUrl = photoUrl;
        this.specialties = specialties;
        this.qualifications = qualifications;
        this.biography = biography;
        this.consultationInformation = consultationInformation;
    }
    public UUID getDoctorProfileId() { return doctorProfileId; }
    public UUID doctorProfileId() { return doctorProfileId; }
    public String getFullName() { return fullName; }
    public String fullName() { return fullName; }
    public String getPhotoUrl() { return photoUrl; }
    public String photoUrl() { return photoUrl; }
    public String getSpecialties() { return specialties; }
    public String specialties() { return specialties; }
    public String getQualifications() { return qualifications; }
    public String qualifications() { return qualifications; }
    public String getBiography() { return biography; }
    public String biography() { return biography; }
    public String getConsultationInformation() { return consultationInformation; }
    public String consultationInformation() { return consultationInformation; }
}
