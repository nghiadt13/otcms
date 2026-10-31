package fu.fa26.sep490.g58.otcms_be.staff.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public class DoctorProfileRequest {
    private @NotNull UUID accountId;
    private @NotBlank @Size(max = 160) String fullName;
    private @Size(max = 2048) String photoUrl;
    private @Size(max = 254) String workContact;
    private String specialties;
    private String qualifications;
    private String biography;
    private String consultationInformation;
    public DoctorProfileRequest() { }
    public DoctorProfileRequest(@NotNull UUID accountId, @NotBlank @Size(max = 160) String fullName, @Size(max = 2048) String photoUrl, @Size(max = 254) String workContact, String specialties, String qualifications, String biography, String consultationInformation) {
        this.accountId = accountId;
        this.fullName = fullName;
        this.photoUrl = photoUrl;
        this.workContact = workContact;
        this.specialties = specialties;
        this.qualifications = qualifications;
        this.biography = biography;
        this.consultationInformation = consultationInformation;
    }
    public UUID getAccountId() { return accountId; }
    public UUID accountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }
    public String getFullName() { return fullName; }
    public String fullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhotoUrl() { return photoUrl; }
    public String photoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public String getWorkContact() { return workContact; }
    public String workContact() { return workContact; }
    public void setWorkContact(String workContact) { this.workContact = workContact; }
    public String getSpecialties() { return specialties; }
    public String specialties() { return specialties; }
    public void setSpecialties(String specialties) { this.specialties = specialties; }
    public String getQualifications() { return qualifications; }
    public String qualifications() { return qualifications; }
    public void setQualifications(String qualifications) { this.qualifications = qualifications; }
    public String getBiography() { return biography; }
    public String biography() { return biography; }
    public void setBiography(String biography) { this.biography = biography; }
    public String getConsultationInformation() { return consultationInformation; }
    public String consultationInformation() { return consultationInformation; }
    public void setConsultationInformation(String consultationInformation) { this.consultationInformation = consultationInformation; }
}
