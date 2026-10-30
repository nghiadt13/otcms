package fu.fa26.sep490.g58.otcms_be.staff.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "doctor_profiles")
public class DoctorProfileJpaEntity {
    @Id private UUID id;
    @Column(name = "account_id", nullable = false, unique = true) private UUID accountId;
    @Column(name = "full_name", nullable = false, length = 160) private String fullName;
    @Column(name = "photo_url", length = 2048) private String photoUrl;
    @Column(name = "work_contact", length = 254) private String workContact;
    @Column(nullable = false, columnDefinition = "text") private String specialties;
    @Column(nullable = false, columnDefinition = "text") private String qualifications;
    @Column(nullable = false, columnDefinition = "text") private String biography;
    @Column(name = "consultation_information", nullable = false, columnDefinition = "text") private String consultationInformation;
    @Column(name = "publicly_visible", nullable = false) private boolean publiclyVisible;
    @Column(name = "accepting_new_bookings", nullable = false) private boolean acceptingNewBookings;
    @Column(name = "in_active_directory", nullable = false) private boolean inActiveDirectory;
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false) private Instant updatedAt;

    protected DoctorProfileJpaEntity() { }

    public DoctorProfileJpaEntity(UUID id, UUID accountId, String fullName, String photoUrl, String workContact,
                           String specialties, String qualifications, String biography, String consultationInformation,
                           boolean publiclyVisible, boolean acceptingNewBookings, boolean inActiveDirectory) {
        this.id = id; this.accountId = accountId; this.fullName = fullName; this.photoUrl = photoUrl;
        this.workContact = workContact; this.specialties = specialties; this.qualifications = qualifications;
        this.biography = biography; this.consultationInformation = consultationInformation;
        this.publiclyVisible = publiclyVisible; this.acceptingNewBookings = acceptingNewBookings;
        this.inActiveDirectory = inActiveDirectory;
    }

    @PreUpdate void touch() { updatedAt = Instant.now(); }
    public UUID id() { return id; }
    public UUID accountId() { return accountId; }
    public String fullName() { return fullName; }
    public String photoUrl() { return photoUrl; }
    public String workContact() { return workContact; }
    public String specialties() { return specialties; }
    public String qualifications() { return qualifications; }
    public String biography() { return biography; }
    public String consultationInformation() { return consultationInformation; }
    public boolean publiclyVisible() { return publiclyVisible; }
    public boolean acceptingNewBookings() { return acceptingNewBookings; }
    public boolean inActiveDirectory() { return inActiveDirectory; }
    public void updateDetails(String fullName, String photoUrl, String workContact, String specialties,
            String qualifications, String biography, String consultationInformation) {
        this.fullName = fullName; this.photoUrl = photoUrl; this.workContact = workContact;
        this.specialties = specialties; this.qualifications = qualifications;
        this.biography = biography; this.consultationInformation = consultationInformation;
    }
    public void updateDirectoryState(boolean publiclyVisible, boolean acceptingNewBookings, boolean inActiveDirectory) {
        this.publiclyVisible = publiclyVisible;
        this.acceptingNewBookings = acceptingNewBookings;
        this.inActiveDirectory = inActiveDirectory;
    }
}
