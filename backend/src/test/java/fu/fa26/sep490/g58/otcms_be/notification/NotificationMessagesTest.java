package fu.fa26.sep490.g58.otcms_be.notification;

import fu.fa26.sep490.g58.otcms_be.notification.model.*;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class NotificationMessagesTest {
    static final UUID ID = UUID.randomUUID();
    static final Instant AT = Instant.parse("2026-11-01T18:30:00Z");
    static final String DOCTOR = "Nguyễn Văn An";
    static Stream<Arguments> messages() {
        return Stream.of(
                Arguments.of("APPOINTMENT_BOOKED", NotificationMessages.appointmentBooked(ID, AT, DOCTOR), NotificationRelatedKind.APPOINTMENT),
                Arguments.of("APPOINTMENT_CANCELLED", NotificationMessages.appointmentCancelled(ID, AT, DOCTOR), NotificationRelatedKind.APPOINTMENT),
                Arguments.of("APPOINTMENT_DOCTOR_CHANGED", NotificationMessages.appointmentDoctorChanged(ID, AT, DOCTOR), NotificationRelatedKind.APPOINTMENT),
                Arguments.of("APPOINTMENT_RESCHEDULED", NotificationMessages.appointmentRescheduled(ID, AT, DOCTOR), NotificationRelatedKind.APPOINTMENT),
                Arguments.of("VISIT_CLOSED_REVIEW_INVITE", NotificationMessages.visitClosedReviewInvite(ID, AT, DOCTOR), NotificationRelatedKind.VISIT),
                Arguments.of("REVIEW_SUBMITTED", NotificationMessages.reviewSubmitted(ID, AT, DOCTOR, 5), NotificationRelatedKind.REVIEW));
    }
    @ParameterizedTest @MethodSource("messages")
    void sixTemplatesUseVietnameseDatesDoctorAndExactJiraTypes(String type, NotificationMessage message, NotificationRelatedKind kind) {
        assertEquals(type, message.type()); assertEquals(kind, message.relatedKind()); assertEquals(ID, message.relatedId());
        assertFalse(message.title().isBlank()); assertTrue(message.content().contains("ngày"));
        assertTrue(message.content().contains("02/11/2026 lúc 01:30"));
        assertTrue(message.content().contains("bác sĩ " + DOCTOR)); assertFalse(message.content().contains("18:30"));
    }
    @Test void historicalReviewWithoutRecordedDoctorDoesNotInventOne() {
        var message = NotificationMessages.reviewSubmitted(ID, AT, "", 4);
        assertTrue(message.content().contains("4/5")); assertTrue(message.content().contains("02/11/2026 lúc 01:30"));
        assertFalse(message.content().contains("bác sĩ")); assertFalse(message.content().contains("null"));
    }
}
