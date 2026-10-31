package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import java.nio.charset.StandardCharsets;

/** Existing local-password limits, including BCrypt's UTF-8 byte limit. */
final class PasswordPolicy {
    private PasswordPolicy() { }

    static boolean isValid(String password) {
        return password != null && password.length() >= 12 && password.length() <= 64
                && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
