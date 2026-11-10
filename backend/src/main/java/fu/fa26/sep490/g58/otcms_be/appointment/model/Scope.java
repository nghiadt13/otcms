package fu.fa26.sep490.g58.otcms_be.appointment.model;

import java.util.Set;
import java.util.UUID;

public class Scope {
    private final ScopeType type;
    private final UUID profileId;
    private final Set<UUID> profileIds;
    public Scope(ScopeType type, UUID profileId) {
        this(type, profileId, Set.of());
    }
    private Scope(ScopeType type, UUID profileId, Set<UUID> profileIds) {
        this.type = type;
        this.profileId = profileId;
        this.profileIds = profileIds;
    }
    /** Bệnh nhân xem lịch của mọi hồ sơ tài khoản được phép truy cập (bản thân và người thân đã xác nhận). */
    public static Scope patient(Set<UUID> profileIds) {
        return new Scope(ScopeType.PATIENT, null, Set.copyOf(profileIds));
    }
    public ScopeType getType() { return type; }
    public ScopeType type() { return type; }
    public UUID getProfileId() { return profileId; }
    public UUID profileId() { return profileId; }
    public Set<UUID> profileIds() { return profileIds; }
}
