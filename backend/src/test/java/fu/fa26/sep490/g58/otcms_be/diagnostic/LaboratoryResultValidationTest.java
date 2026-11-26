package fu.fa26.sep490.g58.otcms_be.diagnostic;

import static org.junit.jupiter.api.Assertions.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.model.LaboratoryResultValue;
import fu.fa26.sep490.g58.otcms_be.diagnostic.validation.LaboratoryResultValidation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LaboratoryResultValidationTest {
    @Test void missingUnitCanBeDraftButCannotBeReleased() {
        var values = List.of(new LaboratoryResultValue("CRP", "2.1", null, "0–5", null, false));
        assertTrue(LaboratoryResultValidation.validDraft(values));
        assertFalse(LaboratoryResultValidation.readyForRelease(values));
    }
    @Test void completeTranscriptionCanBeReleased() {
        var values = List.of(new LaboratoryResultValue("CRP", "2.1", "mg/L", "0–5", null, false));
        assertTrue(LaboratoryResultValidation.readyForRelease(values));
    }
    @Test void missingValueIsNotAValidDraft() {
        assertFalse(LaboratoryResultValidation.validDraft(List.of(new LaboratoryResultValue("CRP", " ", null, null, null, null))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-3.5", "abc", "3mg/L", "NaN", "Infinity", "1e3", "1,5"})
    void negativeOrNonDecimalResultsAreRejected(String value) {
        assertFalse(LaboratoryResultValidation.validDraft(
                List.of(new LaboratoryResultValue("CRP", value, null, null, null, null))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "3", "3.5", ".5", "0.01"})
    void nonNegativeDecimalResultsAreAccepted(String value) {
        assertTrue(LaboratoryResultValidation.validDraft(
                List.of(new LaboratoryResultValue("CRP", value, null, null, null, null))));
    }
}
