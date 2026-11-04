package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.imaging.integration.diagnostic.ImagingWorkAuthorizationPort;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingAssignedWork;
import fu.fa26.sep490.g58.otcms_be.imaging.service.ImagingWorkAuthorizationPolicy;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Application-service orchestration; registered as a bean after the FEAT-003 adapter is wired. */
@Service
public class ImagingAssignedWorkAuthorizationService {
    private final ImagingWorkAuthorizationPort ownerPort;
    private final ImagingWorkAuthorizationPolicy policy;

    public ImagingAssignedWorkAuthorizationService(ImagingWorkAuthorizationPort ownerPort,
            ImagingWorkAuthorizationPolicy policy) {
        this.ownerPort = ownerPort;
        this.policy = policy;
    }

    public ImagingAssignedWork requireAssignedWork(UUID actorAccountId, UUID requestId, UUID attemptId) {
        ImagingAssignedWork ownerDecision = ownerPort.requireAssignedImagingWork(
                actorAccountId, requestId, attemptId);
        return policy.requireAssignedWork(actorAccountId, requestId, attemptId, ownerDecision);
    }
}
