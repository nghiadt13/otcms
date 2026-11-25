package fu.fa26.sep490.g58.otcms_be.appointment.exception;

/**
 * Maps technical appointment failure codes to the R3 MSG code shown to the user. The handlers keep their
 * own HTTP status tables; only the code placed in the error body changes here. Codes that already are
 * MSG codes pass through unchanged. Codes without a dedicated R3 match fall back to MSG-04.
 */
final class AppointmentMsgCodes {
    private AppointmentMsgCodes() { }

    static String of(String technical) {
        if (technical.startsWith("MSG-")) return technical;
        return switch (technical) {
            case "ACCESS_DENIED" -> "MSG-50";
            case "APPOINTMENT_NOT_FOUND", "PATIENT_PROFILE_NOT_FOUND", "SOURCE_VISIT_NOT_FOUND",
                    "SERVICE_NOT_BOOKABLE", "NO_SHOW_NOT_ALLOWED", "FOLLOW_UP_NOT_ALLOWED" -> "MSG-29";
            case "APPOINTMENT_NOT_CHANGEABLE", "APPOINTMENT_CHANGE_TOO_LATE", "CHANGE_DEADLINE_PASSED" -> "MSG-32";
            case "DOCTOR_NOT_ELIGIBLE" -> "MSG-24";
            case "SLOT_UNAVAILABLE", "VISIT_CAPACITY_NOT_AVAILABLE", "BOOKING_TIME_IN_PAST",
                    "BOOKING_NOTICE_TOO_SHORT" -> "MSG-30";
            case "VALIDATION_FAILED", "BOOKING_WINDOW_VIOLATION" -> "MSG-31";
            case "CHECK_IN_TIME_NOT_ALLOWED", "ARRIVAL_PROOF_INVALID" -> "MSG-37";
            case "PATIENT_IDENTITY_MISMATCH" -> "MSG-18";
            case "PATIENT_HAS_OPEN_VISIT" -> technical;
            case "VISIT_LINK_CONFLICT", "VISIT_CANCELLATION_NOT_ALLOWED", "IDEMPOTENCY_CONFLICT",
                    "ARRIVAL_RECORD_CONFLICT", "VISIT_EXAM_STATE_CONFLICT", "VISIT_COMPLETION_NOT_ALLOWED" -> "MSG-42";
            default -> "MSG-04"; // includes DEPENDENCY_UNAVAILABLE and ARRIVAL_PROOF_UNAVAILABLE
        };
    }
}
