package fu.fa26.sep490.g58.otcms_be.auth.model.entity;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.Gender;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.PhoneOwnerType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name = "patient_profiles")
@Getter
@Setter
@NoArgsConstructor
public class PatientProfileJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "account_id", unique = true) private UUID accountId;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "account_id", insertable = false, updatable = false) private AccountJpaEntity account;
    @Column(name = "full_name", nullable = false, length = 100) private String fullName;
    @Column(name = "phone_canonical", nullable = false, length = 16) private String phoneCanonical;
    @Enumerated(EnumType.STRING) @Column(name = "phone_owner_type", nullable = false, length = 16) private PhoneOwnerType phoneOwnerType;
    @Column(name = "date_of_birth", nullable = false) private LocalDate dateOfBirth;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private Gender gender;
}
