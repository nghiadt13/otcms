package fu.fa26.sep490.g58.otcms_be.visit.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "rooms")
public class VisitRoomJpaEntity {
    @Id
    private UUID id;

    @Column(name = "room_code", nullable = false, unique = true, length = 64)
    private String roomCode;

    @Column(name = "display_label", nullable = false, length = 128)
    private String displayLabel;

    @Column(name = "room_type", nullable = false, length = 16)
    private String type;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "room_capabilities", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "capability_code", nullable = false, length = 64)
    private Set<String> capabilities = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "room_supported_services", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "service_id", nullable = false)
    private Set<UUID> supportedServiceIds = new LinkedHashSet<>();

    protected VisitRoomJpaEntity() { }

    public VisitRoomJpaEntity(UUID id, String roomCode, String displayLabel, boolean active,
            Instant createdAt, Instant updatedAt, Set<String> capabilities) {
        this(id, roomCode, displayLabel, "EXAMINATION", "", active, createdAt, updatedAt,
                capabilities, Set.of());
    }

    public VisitRoomJpaEntity(UUID id, String roomCode, String displayLabel, String type, String description,
            boolean active, Instant createdAt, Instant updatedAt, Set<String> capabilities,
            Set<UUID> supportedServiceIds) {
        this.id = id;
        this.roomCode = roomCode;
        this.displayLabel = displayLabel;
        this.type = type;
        this.description = description;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.capabilities = new LinkedHashSet<>(capabilities);
        this.supportedServiceIds = new LinkedHashSet<>(supportedServiceIds);
    }

    public UUID getId() { return id; }
    public String getRoomCode() { return roomCode; }
    public String getDisplayLabel() { return displayLabel; }
    public String getType() { return type; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Set<String> getCapabilities() { return Set.copyOf(capabilities); }
    public Set<UUID> getSupportedServiceIds() { return Set.copyOf(supportedServiceIds); }

    public void update(String displayLabel, Set<String> capabilities, Instant updatedAt) {
        update(displayLabel, description, capabilities, supportedServiceIds, updatedAt);
    }

    public void update(String displayLabel, String description, Set<String> capabilities,
            Set<UUID> supportedServiceIds, Instant updatedAt) {
        this.displayLabel = displayLabel;
        this.description = description;
        this.capabilities.clear();
        this.capabilities.addAll(capabilities);
        this.supportedServiceIds.clear();
        this.supportedServiceIds.addAll(supportedServiceIds);
        this.updatedAt = updatedAt;
    }

    public void setActive(boolean active, Instant updatedAt) {
        this.active = active;
        this.updatedAt = updatedAt;
    }
}
