package fu.fa26.sep490.g58.otcms_be.staff.service;

import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorProfile;

    public interface ProvisionDoctorProfileService {
    DoctorProfile provision(Command command);

        static class Command {
    private final java.util.UUID accountId;
    private final String fullName;
    private final String photoUrl;
    private final String workContact;
    private final String specialties;
    private final String qualifications;
    private final String biography;
    private final String consultationInformation;
    public Command(java.util.UUID accountId, String fullName, String photoUrl, String workContact, String specialties, String qualifications, String biography, String consultationInformation) {
        this.accountId = accountId;
        this.fullName = fullName;
        this.photoUrl = photoUrl;
        this.workContact = workContact;
        this.specialties = specialties;
        this.qualifications = qualifications;
        this.biography = biography;
        this.consultationInformation = consultationInformation;
    }
    public java.util.UUID getAccountId() { return accountId; }
    public java.util.UUID accountId() { return accountId; }
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
}
}
