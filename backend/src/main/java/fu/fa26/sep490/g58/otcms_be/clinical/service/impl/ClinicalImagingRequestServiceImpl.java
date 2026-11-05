package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalImagingRequestService;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates retries only after the transactional attempt proxy has rolled back. */
@Service
public class ClinicalImagingRequestServiceImpl implements ClinicalImagingRequestService {
    static final String REPLAY_KEY = "uk_clinical_imaging_replay_doctor_key";
    static final String ROUND_NUMBER = "uk_clinical_round_visit_number";
    private static final int MAX_ATTEMPTS = 3;
    private final ClinicalImagingRequestTransactionService transactions;

    public ClinicalImagingRequestServiceImpl(ClinicalImagingRequestTransactionService transactions) {
        this.transactions = transactions;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SubmissionResult submit(ImagingRequestCommand supplied) {
        var command = ClinicalImagingRequestFingerprint.validated(supplied);
        String fingerprint = ClinicalImagingRequestFingerprint.fingerprint(command);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return transactions.submit(command, fingerprint);
            } catch (DataIntegrityViolationException failure) {
                if (hasUniqueConstraint(failure, REPLAY_KEY)) {
                    return transactions.recoverReplay(command, fingerprint).orElseThrow(() -> failure);
                }
                if (!hasUniqueConstraint(failure, ROUND_NUMBER)) throw failure;
                // A same-key winner may have committed too. Next attempt checks replay before any write.
                if (attempt == MAX_ATTEMPTS - 1) throw new ClinicalOperationException("MSG-42");
            }
        }
        throw new IllegalStateException("Unreachable retry outcome");
    }

    private static boolean hasUniqueConstraint(Throwable failure, String expected) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && "23505".equals(violation.getSQLState())
                    && expected.equals(violation.getConstraintName())) return true;
            if (cause instanceof SQLException sql) {
                for (SQLException next = sql; next != null; next = next.getNextException()) {
                    if ("23505".equals(next.getSQLState()) && next.getMessage() != null
                            && next.getMessage().contains("\"" + expected + "\"")) return true;
                }
            }
        }
        return false;
    }
}
