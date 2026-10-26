package fu.fa26.sep490.g58.otcms_be.appointment.model;

import java.util.UUID;

public class Scope {
    private final ScopeType type;
    private final UUID profileId;
    public Scope(ScopeType type, UUID profileId) {
        this.type = type;
        this.profileId = profileId;
    }
    public ScopeType getType() { return type; }
    public ScopeType type() { return type; }
    public UUID getProfileId() { return profileId; }
    public UUID profileId() { return profileId; }
}
