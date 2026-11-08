package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Trusted owner facts for UC28/UC44, without Visit authorization or external Imaging calls. */
public interface DiagnosticVisitCompletionEvidenceService {
    ImagingCompletionEvidence getCompletionEvidence(UUID visitId, UUID patientId, Collection<UUID> serviceOrderIds);

    /** Current effective completed/released findings, including legacy requests without a Clinical order. */
    Set<UUID> findCompletedRequestIds(Collection<UUID> requestIds);

    record ImagingCompletionEvidence(UUID visitId, UUID patientId, Set<UUID> requiredServiceOrderIds,
            Set<UUID> completedServiceOrderIds) {
        public ImagingCompletionEvidence {
            Objects.requireNonNull(visitId, "visitId");
            Objects.requireNonNull(patientId, "patientId");
            requiredServiceOrderIds = Set.copyOf(requiredServiceOrderIds);
            completedServiceOrderIds = Set.copyOf(completedServiceOrderIds);
            if (!requiredServiceOrderIds.containsAll(completedServiceOrderIds)) {
                throw new IllegalArgumentException("completed orders must belong to the requested scope");
            }
        }
        public boolean allComplete() { return completedServiceOrderIds.equals(requiredServiceOrderIds); }
    }
}
