package fu.fa26.sep490.g58.otcms_be.auth.model.dto.response;

public class CsrfTokenResponse {
    private final String csrfToken;

    public CsrfTokenResponse(String csrfToken) {
        this.csrfToken = csrfToken;
    }

    public String getCsrfToken() {
        return csrfToken;
    }

    public String csrfToken() {
        return csrfToken;
    }
}
