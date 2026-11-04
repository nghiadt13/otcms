package fu.fa26.sep490.g58.otcms_be.patient.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.junit.jupiter.api.Assertions.*;

class DependentEmailMaskingTest {
    @ParameterizedTest
    @CsvSource({"alice@example.com,***@example.com", "a@example.com,***@example.com"})
    void ordinaryAndShortLocalPartsAreFullyMasked(String email, String expected) {
        assertEquals(expected, DependentProfileServiceImpl.maskEmail(email));
    }
    @Test void quotedLocalPartUsesFinalDomainDelimiter() {
        String masked = DependentProfileServiceImpl.maskEmail("\"alice@internal\"@example.test");
        assertEquals("***@example.test", masked);
        assertFalse(masked.contains("alice")); assertFalse(masked.contains("internal"));
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "invalid", "@example.test", "a@"})
    void missingOrMalformedInputCannotExposeRawData(String email) {
        assertEquals("***", DependentProfileServiceImpl.maskEmail(email));
    }
}
