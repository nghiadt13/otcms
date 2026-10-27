package fu.fa26.sep490.g58.otcms_be.auth.exception;

public class GoogleAuthenticationException extends RuntimeException {
    public GoogleAuthenticationException(String code) { super(code); }
}
