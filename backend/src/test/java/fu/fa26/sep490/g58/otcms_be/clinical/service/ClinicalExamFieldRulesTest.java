package fu.fa26.sep490.g58.otcms_be.clinical.service;

import static org.junit.jupiter.api.Assertions.*;

import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class ClinicalExamFieldRulesTest {
    private static final List<String> DIRECTIONS = List.of("HOME_CARE");

    @Test
    void completionRequiresBodySiteSideMechanismDescriptionAndDirections() {
        ClinicalExamFieldRules.validate("Gối", "LEFT", "Ngã", "Sưng", List.of("HOME_CARE", "PROCEDURE_ELSEWHERE"), true);
        assertInvalid(() -> ClinicalExamFieldRules.validate(null, "LEFT", "Ngã", "Sưng", DIRECTIONS, true));
        assertInvalid(() -> ClinicalExamFieldRules.validate(" ", "LEFT", "Ngã", "Sưng", DIRECTIONS, true));
        assertInvalid(() -> ClinicalExamFieldRules.validate("Gối", null, "Ngã", "Sưng", DIRECTIONS, true));
        assertInvalid(() -> ClinicalExamFieldRules.validate("Gối", "Trái", "Ngã", "Sưng", DIRECTIONS, true));
        assertInvalid(() -> ClinicalExamFieldRules.validate("Gối", "LEFT", " ", "Sưng", DIRECTIONS, true));
        assertInvalid(() -> ClinicalExamFieldRules.validate("Gối", "LEFT", "Ngã", null, DIRECTIONS, true));
        assertInvalid(() -> ClinicalExamFieldRules.validate("Gối", "LEFT", "Ngã", "Sưng", null, true));
        assertInvalid(() -> ClinicalExamFieldRules.validate("Gối", "LEFT", "Ngã", "Sưng", List.of(), true));
    }

    @Test
    void draftAllowsMissingFieldsButRejectsSideOutsideTheDatabaseCodes() {
        ClinicalExamFieldRules.validate("Gối", null, null, null, null, false);
        ClinicalExamFieldRules.validate(null, null, null, null, List.of(), false);
        ClinicalExamFieldRules.validate(null, " LEFT ", null, null, null, false);
        assertInvalid(() -> ClinicalExamFieldRules.validate("Gối", "TOP", null, null, null, false));
        assertInvalid(() -> ClinicalExamFieldRules.validate("Gối", "Trái", null, null, null, false));
        assertInvalid(() -> ClinicalExamFieldRules.validate(null, null, null, null, List.of("OTHER"), false));
        assertInvalid(() -> ClinicalExamFieldRules.validate(null, null, null, null, List.of("HOME_CARE", "HOME_CARE"), false));
        assertInvalid(() -> ClinicalExamFieldRules.validate(null, null, null, null, Arrays.asList("HOME_CARE", null), false));
    }

    @Test
    void lengthLimitsMatchStorage() {
        ClinicalExamFieldRules.validate("x".repeat(120), null, "m".repeat(1000), "d".repeat(2000), null, false);
        assertInvalid(() -> ClinicalExamFieldRules.validate("x".repeat(121), null, null, null, null, false));
        assertInvalid(() -> ClinicalExamFieldRules.validate(null, null, "m".repeat(1001), null, null, false));
        assertInvalid(() -> ClinicalExamFieldRules.validate(null, null, null, "d".repeat(2001), null, false));
    }

    @Test
    void normalizationTrimsTextAndDropsEmptyDirections() {
        assertEquals("Gối", ClinicalExamFieldRules.trimToNull(" Gối "));
        assertNull(ClinicalExamFieldRules.trimToNull("   "));
        assertNull(ClinicalExamFieldRules.trimToNull(null));
        assertNull(ClinicalExamFieldRules.normalizeDirections(null));
        assertNull(ClinicalExamFieldRules.normalizeDirections(List.of()));
        assertEquals(List.of("MEDICATION"), ClinicalExamFieldRules.normalizeDirections(List.of("MEDICATION")));
    }

    @Test
    void labelsAreVietnameseAndUnrecordedValuesAreEmpty() {
        assertEquals("Trái", ClinicalExamFieldRules.sideLabel("LEFT"));
        assertEquals("Phải", ClinicalExamFieldRules.sideLabel("RIGHT"));
        assertEquals("Hai bên", ClinicalExamFieldRules.sideLabel("BOTH"));
        assertEquals("Không áp dụng", ClinicalExamFieldRules.sideLabel("NOT_APPLICABLE"));
        assertNull(ClinicalExamFieldRules.sideLabel(null));
        assertEquals("Chăm sóc tại nhà, Thủ thuật hoặc phẫu thuật tại cơ sở khác",
                ClinicalExamFieldRules.directionText(List.of("HOME_CARE", "PROCEDURE_ELSEWHERE")));
        assertEquals("", ClinicalExamFieldRules.directionText(null));
    }

    private static void assertInvalid(Executable action) {
        assertEquals("MSG-39", assertThrows(ClinicalOperationException.class, action).code());
    }
}
