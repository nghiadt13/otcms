package fu.fa26.sep490.g58.otcms_be.partner.model.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "partners")
public class PartnerJpaEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 255) private String name;
    @Column(length = 255) private String contact;
    @Column(nullable = false) private boolean active;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "partner_category_assignments",
            joinColumns = @JoinColumn(name = "partner_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private java.util.Set<PartnerCategoryJpaEntity> categories = new java.util.LinkedHashSet<>();
    protected PartnerJpaEntity() { }
    public PartnerJpaEntity(String name, String contact) { this.id = UUID.randomUUID(); this.name = name; this.contact = contact; this.active = true; }
    public UUID getId() { return id; } public String getName() { return name; } public String getContact() { return contact; } public boolean isActive() { return active; }
    public java.util.Set<PartnerCategoryJpaEntity> getCategories() { return java.util.Set.copyOf(categories); }
    public void update(String name, String contact, boolean active, java.util.Set<PartnerCategoryJpaEntity> categories) { this.name = name; this.contact = contact; this.active = active; this.categories = new java.util.LinkedHashSet<>(categories); }
}
