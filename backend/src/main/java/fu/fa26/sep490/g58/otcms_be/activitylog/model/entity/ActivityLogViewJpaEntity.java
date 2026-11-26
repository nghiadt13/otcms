package fu.fa26.sep490.g58.otcms_be.activitylog.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/** Read-only mapping of the safe activity-log database view. */
@Entity
@Immutable
@Table(name = "activity_log_entries")
public class ActivityLogViewJpaEntity {
    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    protected ActivityLogViewJpaEntity() { }
}
