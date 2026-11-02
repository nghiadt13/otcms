package fu.fa26.sep490.g58.otcms_be.content.application;
public final class ContentFailure extends RuntimeException {
    private final int status;
    public ContentFailure(String code, int status) { super(code); this.status = status; }
    public int status() { return status; }
}
