package fu.fa26.sep490.g58.otcms_be.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "authentication_audits") @Getter @Setter @NoArgsConstructor
public class AuthenticationAudit {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(nullable = false, length = 64) private String method;
    @Column(nullable = false, length = 64) private String outcome;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "account_id") private Account account;
    @Column(name = "identifier_hmac", length = 255) private String identifierHmac;
    @Column(name = "correlation_id", nullable = false, length = 128) private String correlationId;
    @Column(name = "client_ip", length = 64) private String clientIp;
    @Column(name = "user_agent", length = 512) private String userAgent;
    @Column(name = "duration_ms", nullable = false) private long durationMs;
}
