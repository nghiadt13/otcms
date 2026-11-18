package fu.fa26.sep490.g58.otcms_be.appointment.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AppointmentMsgCodesTest {
    @ParameterizedTest
    @CsvSource({
        "SLOT_UNAVAILABLE,MSG-30",
        "BOOKING_TIME_IN_PAST,MSG-30",
        "DOCTOR_NOT_ELIGIBLE,MSG-24",
        "APPOINTMENT_NOT_FOUND,MSG-29",
        "APPOINTMENT_CHANGE_TOO_LATE,MSG-32",
        "VALIDATION_FAILED,MSG-31",
        "VISIT_LINK_CONFLICT,MSG-42",
        "ACCESS_DENIED,MSG-50",
        "DEPENDENCY_UNAVAILABLE,MSG-04",
    })
    void mapsTechnicalCodeToR3MessageCode(String technical, String msgCode) {
        assertEquals(msgCode, AppointmentMsgCodes.of(technical));
    }

    @Test
    void existingMsgCodePassesThroughUnchanged() {
        assertEquals("MSG-94", AppointmentMsgCodes.of("MSG-94"));
    }

    @Test
    void unknownCodeFallsBackToGenericMsg04() {
        assertEquals("MSG-04", AppointmentMsgCodes.of("SOME_UNMAPPED_CODE"));
    }
}
