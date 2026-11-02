package fu.fa26.sep490.g58.otcms_be.patient.exception;

/** Only this failure commits the deliberately recorded failed-attempt evidence. */
public class CareOtpInvalidException extends CareRelationshipException {
    public CareOtpInvalidException() { super("OTP_INVALID"); }
}
