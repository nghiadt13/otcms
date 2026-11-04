package fu.fa26.sep490.g58.otcms_be.diagnostic.exception;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.entity.enums.DiagnosticOperationOutcome;
import java.util.Objects;

/** Domain failure without HTTP-specific status mapping; API mapping is reviewed separately. */
public class DiagnosticOperationException extends RuntimeException {
    private final DiagnosticOperationOutcome outcome;

    public DiagnosticOperationException(DiagnosticOperationOutcome outcome) {
        super(Objects.requireNonNull(outcome, "outcome").name());
        if (outcome == DiagnosticOperationOutcome.ALLOWED) {
            throw new IllegalArgumentException("ALLOWED is not an exception outcome");
        }
        this.outcome = outcome;
    }

    public DiagnosticOperationOutcome outcome() {
        return outcome;
    }
}
