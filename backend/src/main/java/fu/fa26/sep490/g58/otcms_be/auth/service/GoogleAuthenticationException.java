package fu.fa26.sep490.g58.otcms_be.auth.service;

public class GoogleAuthenticationException extends RuntimeException {
    public GoogleAuthenticationException(String code) { super(code); }
}
