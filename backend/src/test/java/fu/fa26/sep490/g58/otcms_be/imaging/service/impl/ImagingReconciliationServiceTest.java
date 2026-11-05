package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.enums.ImagingReconciliationClassification;
import org.junit.jupiter.api.Test;

class ImagingReconciliationServiceTest {
    @Test
    void classifiesIdentityAndReplayConflictsWithoutAutomaticRecovery() {
        assertEquals(ImagingReconciliationClassification.CONFLICT,
                ImagingReconciliationService.classify("IDENTITY_MISMATCH"));
        assertEquals(ImagingReconciliationClassification.CONFLICT,
                ImagingReconciliationService.classify("RELEASE_EVENT_CONFLICT"));
    }

    @Test
    void classifiesAvailableRetryAndUncertainArchiveOutcomes() {
        assertEquals(ImagingReconciliationClassification.RETRYABLE,
                ImagingReconciliationService.classify("RELEASE_BEFORE_LINKED"));
        assertEquals(ImagingReconciliationClassification.RETRYABLE,
                ImagingReconciliationService.classify("ARCHIVE_OR_DATABASE_OUTCOME_UNCERTAIN"));
        assertEquals(ImagingReconciliationClassification.ORPHANED,
                ImagingReconciliationService.classify("IDENTITY_UNKNOWN"));
        assertThrows(IllegalArgumentException.class,
                () -> ImagingReconciliationService.classify("UNCLASSIFIED_REASON"));
    }
}
