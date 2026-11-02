package fu.fa26.sep490.g58.otcms_be.auth.validation;

/** Bounds indexed usernames without normalizing existing case or printable characters. */
public final class AdministratorUsernamePolicy {
    public static final int MAX_LENGTH = 64;

    private AdministratorUsernamePolicy() { }

    public static boolean isValid(String username) {
        return username != null && !username.isBlank() && username.length() <= MAX_LENGTH
                && username.codePoints().noneMatch(codePoint -> Character.isISOControl(codePoint)
                        || Character.getType(codePoint) == Character.SURROGATE);
    }
}
