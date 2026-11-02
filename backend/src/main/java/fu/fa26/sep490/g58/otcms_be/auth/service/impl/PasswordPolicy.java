package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import java.nio.charset.StandardCharsets;

/** BR-04 for new local passwords; login continues to verify legacy credentials unchanged. */
final class PasswordPolicy {
    private PasswordPolicy() { }

    static boolean isValid(String password) {
        if (password == null) return false;
        int length = password.codePointCount(0, password.length());
        return length >= 8 && length <= 64
                && password.getBytes(StandardCharsets.UTF_8).length <= 72
                && password.codePoints().anyMatch(Character::isLetter)
                && password.codePoints().anyMatch(Character::isDigit);
    }
}
