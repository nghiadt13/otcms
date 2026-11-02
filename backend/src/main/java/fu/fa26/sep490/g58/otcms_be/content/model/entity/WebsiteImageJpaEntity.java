package fu.fa26.sep490.g58.otcms_be.content.model.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "website_images")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WebsiteImageJpaEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private byte[] content;
    @Column(length = 32, nullable = false)
    private String contentType;
}
