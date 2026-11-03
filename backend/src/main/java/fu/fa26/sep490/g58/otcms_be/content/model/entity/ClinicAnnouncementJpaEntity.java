package fu.fa26.sep490.g58.otcms_be.content.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "announcements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClinicAnnouncementJpaEntity {
    @Id
    private UUID id;
    @Column(length = 200, nullable = false)
    private String title;
    @Column(columnDefinition = "text", nullable = false)
    private String body;
    @Column(length = 16, nullable = false)
    private String audience;
    @Column(length = 32)
    private String recipientRole;
    @Column(nullable = false)
    private Instant publishedAt;
    @Column(nullable = false)
    private UUID createdBy;
    @Version
    private Long version;
}
