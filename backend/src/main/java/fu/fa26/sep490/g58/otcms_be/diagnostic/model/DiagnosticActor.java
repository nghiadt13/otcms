package fu.fa26.sep490.g58.otcms_be.diagnostic.model;

import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import java.util.Objects;
import java.util.UUID;

/** Authenticated actor supplied by the service boundary, never by a request DTO. */
public final class DiagnosticActor {
    public enum Kind { USER, TRUSTED_INTEGRATION, SYSTEM }

    private final UUID accountId;
    private final AccountRole role;
    private final Kind kind;

    public DiagnosticActor(UUID accountId, AccountRole role, Kind kind) {
        this.accountId = accountId;
        this.role = role;
        this.kind = kind;
        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.USER) {
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(role, "role");
        } else if (role != null || accountId != null) {
            throw new IllegalArgumentException("Non-user actors cannot carry an account ID or application role");
        }
    }

    public UUID accountId() { return accountId; }
    public AccountRole role() { return role; }
    public Kind kind() { return kind; }

    public static DiagnosticActor user(UUID accountId, AccountRole role) {
        return new DiagnosticActor(accountId, role, Kind.USER);
    }

    public static DiagnosticActor trustedIntegration() {
        return new DiagnosticActor(null, null, Kind.TRUSTED_INTEGRATION);
    }

    public static DiagnosticActor system() {
        return new DiagnosticActor(null, null, Kind.SYSTEM);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DiagnosticActor that)) return false;
        return Objects.equals(accountId, that.accountId) && role == that.role && kind == that.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, role, kind);
    }

    @Override
    public String toString() {
        return "DiagnosticActor[accountId=" + accountId + ", role=" + role + ", kind=" + kind + "]";
    }
}
