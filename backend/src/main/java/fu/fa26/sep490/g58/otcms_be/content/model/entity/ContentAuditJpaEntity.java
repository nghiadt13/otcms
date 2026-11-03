package fu.fa26.sep490.g58.otcms_be.content.model.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "content_audits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@org.hibernate.annotations.Immutable
public class ContentAuditJpaEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID actorAccountId;
    @Column(length = 32, nullable = false)
    private String category;
    @Column(length = 32, nullable = false)
    private String action;
    @Column(nullable = false)
    private UUID recordId;
    @Column(length = 200, nullable = false)
    private String recordLabel;
    @Column(nullable = false)
    private Instant occurredAt;
}
