package fu.fa26.sep490.g58.otcms_be.appointment.exception;

public final class SlotAvailabilityException extends RuntimeException {
    private final String code;

    public SlotAvailabilityException(String code) {
        super(code);
        this.code = code;
    }

    public String code() { return code; }
}
