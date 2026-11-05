package fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums;

import java.util.EnumSet;
import java.util.Set;

/** Persisted ingest state vocabulary. Transitions deliberately fail closed. */
public enum ImagingOperationState {
    PROCESSING,
    ARCHIVED_NOT_SENT,
    CONFIRMATION_PENDING,
    LINKED,
    QUARANTINED,
    FAILED,
    RECONCILIATION_REQUIRED;

    public boolean canTransitionTo(ImagingOperationState next) {
        if (next == null || next == this) return false;
        return TRANSITIONS.get(this).contains(next);
    }

    public void requireTransitionTo(ImagingOperationState next) {
        if (!canTransitionTo(next)) {
            throw new IllegalArgumentException("Invalid imaging operation state transition");
        }
    }

    private static final java.util.Map<ImagingOperationState, Set<ImagingOperationState>> TRANSITIONS =
            java.util.Map.of(
                    PROCESSING, EnumSet.of(ARCHIVED_NOT_SENT, QUARANTINED, FAILED, RECONCILIATION_REQUIRED),
                    ARCHIVED_NOT_SENT, EnumSet.of(CONFIRMATION_PENDING),
                    CONFIRMATION_PENDING, EnumSet.of(LINKED, RECONCILIATION_REQUIRED),
                    QUARANTINED, EnumSet.of(RECONCILIATION_REQUIRED),
                    LINKED, EnumSet.noneOf(ImagingOperationState.class),
                    FAILED, EnumSet.noneOf(ImagingOperationState.class),
                    RECONCILIATION_REQUIRED, EnumSet.noneOf(ImagingOperationState.class));
}
