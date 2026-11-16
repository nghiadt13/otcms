package fu.fa26.sep490.g58.otcms_be.staff.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * One profile per internal account. Since final-db V110 it also carries the doctor fields that used to live in
 * the old doctor profile table; {@code is_doctor} tells doctors apart from other staff. Every "doctor" lookup must filter on it.
 */
@Entity
@Table(name = "staff_profiles")
public class StaffProfileJpaEntity {
    @Id private UUID id;
    @Column(name = "account_id", nullable = false, unique = true) private UUID accountId;
    @Column(name = "display_name", nullable = false, length = 160) private String displayName;
    @Column(name = "work_contact", length = 254) private String workContact;
    @Column(name = "photo_content") private byte[] photoContent;
    @Column(name = "photo_content_type", length = 32) private String photoContentType;
    @Column(name = "photo_url", length = 2048) private String photoUrl;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "is_doctor", nullable = false) private boolean doctor;
    @Column(nullable = false, columnDefinition = "text") private String specialties = "";
    @Column(nullable = false, columnDefinition = "text") private String qualifications = "";
    @Column(nullable = false, columnDefinition = "text") private String biography = "";
    @Column(name = "consultation_information", nullable = false, columnDefinition = "text")
    private String consultationInformation = "";
    @Column(name = "publicly_visible", nullable = false) private boolean publiclyVisible;
    @Column(name = "accepting_new_bookings", nullable = false) private boolean acceptingNewBookings;
    @Column(name = "in_active_directory", nullable = false) private boolean inActiveDirectory = true;
    @Column(name = "created_at", insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;

    protected StaffProfileJpaEntity() { }

    public StaffProfileJpaEntity(UUID id, UUID accountId, String displayName, String workContact) {
        this.id = id;
        this.accountId = accountId;
        this.displayName = displayName;
        this.workContact = workContact;
        this.status = "ACTIVE";
    }

    public UUID id() { return id; }
    public UUID accountId() { return accountId; }
    public String displayName() { return displayName; }
    public String workContact() { return workContact; }
    public byte[] photoContent() { return photoContent; }
    public String photoContentType() { return photoContentType; }
    public String photoUrl() { return photoUrl; }
    public String status() { return status; }
    public boolean isDoctor() { return doctor; }
    public String specialties() { return specialties; }
    public String qualifications() { return qualifications; }
    public String biography() { return biography; }
    public String consultationInformation() { return consultationInformation; }
    public boolean publiclyVisible() { return publiclyVisible; }
    public boolean acceptingNewBookings() { return acceptingNewBookings; }
    public boolean inActiveDirectory() { return inActiveDirectory; }

    public void update(String name, String contact) { displayName = name; workContact = contact; }
    public void photo(byte[] content, String type) { photoContent = content; photoContentType = type; }
    public void remove() { status = "REMOVED"; }

    /** Marks this profile as a doctor. New doctors start in the directory but not accepting bookings. */
    public void becomeDoctor(String specialties, String qualifications, String biography,
            String consultationInformation, boolean publiclyVisible) {
        this.doctor = true;
        this.specialties = specialties;
        this.qualifications = qualifications;
        this.biography = biography;
        this.consultationInformation = consultationInformation;
        this.publiclyVisible = publiclyVisible;
        this.inActiveDirectory = true;
        this.acceptingNewBookings = false;
    }

    /** Doctor detail update; name maps to display_name. */
    public void updateDoctor(String name, String photoUrl, String workContact, String specialties,
            String qualifications, String biography, String consultationInformation) {
        this.displayName = name;
        this.photoUrl = photoUrl;
        this.workContact = workContact;
        this.specialties = specialties;
        this.qualifications = qualifications;
        this.biography = biography;
        this.consultationInformation = consultationInformation;
    }

    /** Doctor profile update from the staff screen. */
    public void updateDoctorProfile(String name, String contact, String specialties, String qualifications,
            String biography, String consultationInformation, boolean visible) {
        this.displayName = name;
        this.workContact = contact;
        this.specialties = specialties;
        this.qualifications = qualifications;
        this.biography = biography;
        this.consultationInformation = consultationInformation;
        this.publiclyVisible = visible;
    }

    public void updateDirectoryState(boolean publiclyVisible, boolean acceptingNewBookings, boolean inActiveDirectory) {
        this.publiclyVisible = publiclyVisible;
        this.acceptingNewBookings = acceptingNewBookings;
        this.inActiveDirectory = inActiveDirectory;
    }

    public void updatePhotoUrl(String value) { this.photoUrl = value; }

    @PreUpdate void touch() { updatedAt = Instant.now(); }
}
