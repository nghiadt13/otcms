package fu.fa26.sep490.g58.otcms_be.common.model;

import java.util.UUID;

/** Privacy-limited Admin projection; deliberately excludes appointment, patient, and clinical details. */
public class AdminMutationImpact {
    private final String resourceType;
    private final UUID resourceId;
    private final boolean blocked;
    private final long affectedBookingCount;
    private final boolean activeCareBlocks;
    public AdminMutationImpact(String resourceType, UUID resourceId, boolean blocked,
            long affectedBookingCount, boolean activeCareBlocks) {
        this.resourceType=resourceType; this.resourceId=resourceId; this.blocked=blocked;
        this.affectedBookingCount=affectedBookingCount; this.activeCareBlocks=activeCareBlocks;
    }
    public String getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
    public boolean isBlocked() { return blocked; }
    public long getAffectedBookingCount() { return affectedBookingCount; }
    public boolean isActiveCareBlocks() { return activeCareBlocks; }
}
