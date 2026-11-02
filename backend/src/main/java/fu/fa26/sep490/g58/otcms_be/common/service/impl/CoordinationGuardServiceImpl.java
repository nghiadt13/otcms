package fu.fa26.sep490.g58.otcms_be.common.service.impl;

import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.common.exception.CoordinationGuardUnavailableException;
import fu.fa26.sep490.g58.otcms_be.common.repository.CoordinationGuardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CoordinationGuardServiceImpl implements CoordinationGuardService {
    private final CoordinationGuardRepository coordinationGuard;

    public CoordinationGuardServiceImpl(CoordinationGuardRepository coordinationGuard) {
        this.coordinationGuard = coordinationGuard;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire() {
        coordinationGuard.setTransactionTimeouts();
        String isolation = coordinationGuard.transactionIsolation();
        if (!"read committed".equalsIgnoreCase(isolation)) {
            throw new CoordinationGuardUnavailableException();
        }
        if (!coordinationGuard.lockGuardRow()) {
            throw new CoordinationGuardUnavailableException();
        }
    }
}
