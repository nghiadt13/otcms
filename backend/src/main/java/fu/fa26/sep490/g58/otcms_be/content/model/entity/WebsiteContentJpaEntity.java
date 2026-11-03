package fu.fa26.sep490.g58.otcms_be.content.model.entity;

import fu.fa26.sep490.g58.otcms_be.content.model.ContentType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "website_contents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WebsiteContentJpaEntity {
    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = false)
    private ContentType type;
    @Column(length = 200, nullable = false)
    private String title;
    @Column(columnDefinition = "text", nullable = false)
    private String summary;
    @Column(columnDefinition = "text", nullable = false)
    private String body;
    @Column(length = 16, nullable = false)
    private String status;
    private UUID coverImageId;
    @Column(nullable = false)
    private Instant publishedAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Column(nullable = false)
    private UUID createdBy;
    @Column(nullable = false)
    private UUID updatedBy;
    @Version
    private Long version;
}
