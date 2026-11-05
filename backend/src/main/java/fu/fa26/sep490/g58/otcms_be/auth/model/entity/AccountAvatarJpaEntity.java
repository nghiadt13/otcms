package fu.fa26.sep490.g58.otcms_be.auth.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_avatars")
@Getter @Setter @NoArgsConstructor
public class AccountAvatarJpaEntity {
    @Id @Column(name = "account_id") private UUID accountId;
    @Column(name = "photo_content", nullable = false) private byte[] photoContent;
    @Column(name = "photo_content_type", nullable = false, length = 32) private String photoContentType;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
}
