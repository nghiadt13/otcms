package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.model.dto.request.*;
import jakarta.validation.Validation;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {
    static Stream<String> valid() {
        return Stream.of("abcd1234", "a".repeat(63) + "1", "ábcdefg١",
                "á".repeat(35) + "a1", "a".repeat(62) + "𐐀1", " abcd12 ");
    }
    static Stream<String> invalid() {
        return Stream.of(null, "", "abcdef1", "abcdefgh", "12345678", "a".repeat(64) + "1",
                "á".repeat(36) + "a1");
    }
    @ParameterizedTest @MethodSource("valid")
    void acceptsOnlyRequiredCompositionAndUnicodeBoundaries(String value) { assertTrue(PasswordPolicy.isValid(value)); }
    @ParameterizedTest @MethodSource("invalid")
    void rejectsInvalidLengthBytesOrComposition(String value) { assertFalse(PasswordPolicy.isValid(value)); }
    @Test void supplementaryCodePointsAreNotRejectedByAnyPasswordDto() {
        String value = "a".repeat(62) + "𐐀1";
        assertEquals(64, value.codePointCount(0, value.length()));
        assertEquals(65, value.length());
        assertEquals(67, value.getBytes(StandardCharsets.UTF_8).length);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(new RegistrationRequest("Test Patient", "test@example.test", value, value, true)).isEmpty());
            assertTrue(validator.validate(new ResetPasswordRequest("token", value)).isEmpty());
            assertTrue(validator.validate(new ChangePasswordRequest("legacy", value)).isEmpty());
        }
    }
}
