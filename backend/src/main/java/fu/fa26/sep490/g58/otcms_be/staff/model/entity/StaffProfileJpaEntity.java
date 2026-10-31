package fu.fa26.sep490.g58.otcms_be.staff.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "staff_profiles")
public class StaffProfileJpaEntity {
    @Id private UUID id;
    @Column(name = "account_id", nullable = false, unique = true) private UUID accountId;
    @Column(name = "display_name", nullable = false, length = 100) private String displayName;
    @Column(name = "work_contact", nullable = false, length = 100) private String workContact;
    @Column(name = "photo_content") private byte[] photoContent;
    @Column(name = "photo_content_type", length = 32) private String photoContentType;
    @Column(nullable = false, length = 16) private String status;
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
    public String status() { return status; }
    public void update(String name, String contact) { displayName = name; workContact = contact; }
    public void photo(byte[] content, String type) { photoContent = content; photoContentType = type; }
    public void remove() { status = "REMOVED"; }
    @PreUpdate void touch() { updatedAt = Instant.now(); }
}
