package fu.fa26.sep490.g58.otcms_be.notification.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** User-facing notification templates; dates always use the clinic's Vietnamese timezone. */
public final class NotificationMessages {
    public static final String APPOINTMENT_BOOKED = "APPOINTMENT_BOOKED";
    public static final String APPOINTMENT_CANCELLED = "APPOINTMENT_CANCELLED";
    public static final String APPOINTMENT_DOCTOR_CHANGED = "APPOINTMENT_DOCTOR_CHANGED";
    public static final String APPOINTMENT_RESCHEDULED = "APPOINTMENT_RESCHEDULED";
    public static final String VISIT_CLOSED_REVIEW_INVITE = "VISIT_CLOSED_REVIEW_INVITE";
    public static final String REVIEW_SUBMITTED = "REVIEW_SUBMITTED";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/uuuu 'lúc' HH:mm",
            Locale.forLanguageTag("vi-VN")).withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private NotificationMessages() { }
    public static NotificationMessage appointmentBooked(UUID id, Instant at, String doctor) {
        return appointment(APPOINTMENT_BOOKED, "Đặt lịch khám thành công", "Lịch khám ngày " + TIME.format(at)
                + doctor(doctor) + " đã được xác nhận.", id);
    }
    public static NotificationMessage appointmentCancelled(UUID id, Instant at, String doctor) {
        return appointment(APPOINTMENT_CANCELLED, "Lịch khám đã được hủy", "Phòng khám đã hủy lịch khám ngày "
                + TIME.format(at) + doctor(doctor) + ".", id);
    }
    public static NotificationMessage appointmentDoctorChanged(UUID id, Instant at, String doctor) {
        return appointment(APPOINTMENT_DOCTOR_CHANGED, "Thay đổi bác sĩ khám", "Lịch khám ngày " + TIME.format(at)
                + " được cập nhật" + doctor(doctor) + ".", id);
    }
    public static NotificationMessage appointmentRescheduled(UUID id, Instant at, String doctor) {
        return appointment(APPOINTMENT_RESCHEDULED, "Thay đổi giờ khám", "Lịch khám được chuyển sang ngày "
                + TIME.format(at) + doctor(doctor) + ".", id);
    }
    public static NotificationMessage visitClosedReviewInvite(UUID id, Instant at, String doctor) {
        return new NotificationMessage(VISIT_CLOSED_REVIEW_INVITE, "Mời bạn đánh giá phòng khám",
                "Lượt khám hoàn tất ngày " + TIME.format(at) + doctor(doctor) + ". Mời bạn gửi đánh giá về phòng khám.",
                NotificationRelatedKind.VISIT, id);
    }
    public static NotificationMessage reviewSubmitted(UUID id, Instant visitAt, String doctor, int rating) {
        return new NotificationMessage(REVIEW_SUBMITTED, "Đánh giá mới cần duyệt", "Đánh giá " + rating
                + "/5 cho lượt khám ngày " + TIME.format(visitAt) + doctor(doctor) + " đang chờ kiểm duyệt.",
                NotificationRelatedKind.REVIEW, id);
    }
    private static NotificationMessage appointment(String type, String title, String content, UUID id) {
        return new NotificationMessage(type, title, content, NotificationRelatedKind.APPOINTMENT, id);
    }
    private static String doctor(String name) {
        // Historical visits may lack a recorded doctor; never invent a name or reject the source operation.
        return name == null || name.isBlank() ? "" : " với bác sĩ " + name.trim();
    }
}
