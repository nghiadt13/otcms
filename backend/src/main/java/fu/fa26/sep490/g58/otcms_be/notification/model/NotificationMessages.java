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
    public static final String REFUND_AWAITING_CLINIC_CANCELLATION = "REFUND_AWAITING_CLINIC_CANCELLATION";
    public static final String VISIT_INCOMPLETE = "VISIT_INCOMPLETE";
    public static final String VISIT_CHANGED = "VISIT_CHANGED";
    public static final String VISIT_RESULTS_PUBLISHED = "VISIT_RESULTS_PUBLISHED";
    public static final String APPOINTMENT_REMINDER = "APPOINTMENT_REMINDER";
    public static final String FOLLOW_UP_REMINDER = "FOLLOW_UP_REMINDER";
    public static final String FOLLOW_UP_OVERDUE = "FOLLOW_UP_OVERDUE";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/uuuu 'lúc' HH:mm",
            Locale.forLanguageTag("vi-VN")).withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("vi-VN"))
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu",
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
    /** MSG-97: the clinic cancelled the appointment, so its paid fee awaits a manual refund. {@code invoiceId} is the related record. */
    public static NotificationMessage refundAwaitingClinicCancellation(UUID invoiceId, Instant appointmentAt) {
        return new NotificationMessage(REFUND_AWAITING_CLINIC_CANCELLATION, "Khoản phí chờ hoàn lại", "Lịch hẹn ngày "
                + DATE.format(appointmentAt) + " đã bị hủy bởi phòng khám. Khoản phí đã thanh toán sẽ được hoàn lại, "
                + "phòng khám sẽ liên hệ với bạn.", NotificationRelatedKind.PAYMENT, invoiceId);
    }
    /** MSG-82: the 7-day period of a visit ended, so it is closed as Incomplete. */
    public static NotificationMessage visitIncomplete(UUID visitId, Instant visitAt, String patientName) {
        return new NotificationMessage(VISIT_INCOMPLETE, "Lượt khám chưa hoàn tất", "Lượt khám ngày " + DATE.format(visitAt)
                + (patientName == null || patientName.isBlank() ? "" : " của " + patientName.trim())
                + " đã đóng ở trạng thái chưa hoàn tất. Ảnh và kết quả xét nghiệm đã được công bố; các chỉ định chưa thực hiện"
                + " đã bị hủy; khoản đã thanh toán không được hoàn.", NotificationRelatedKind.VISIT, visitId);
    }

    /** MSG-83: the responsible doctor is told that a visit changed (transferred to them, or a result was corrected). */
    public static NotificationMessage visitChanged(UUID visitId, String patientRef, String change) {
        return new NotificationMessage(VISIT_CHANGED, "Lượt khám có thay đổi", "Lượt khám của bệnh nhân " + patientRef
                + " có thay đổi: " + change + ". Vui lòng xem lại.", NotificationRelatedKind.VISIT, visitId);
    }
    /** MSG-78: results of a completed visit are available to the patient. */
    public static NotificationMessage visitResultsPublished(UUID visitId, Instant visitAt, String patientName) {
        return new NotificationMessage(VISIT_RESULTS_PUBLISHED, "Kết quả khám đã được công bố",
                "Kết quả khám ngày " + DATE.format(visitAt) + " của " + patientName
                + " đã được công bố. Vui lòng đăng nhập để xem.", NotificationRelatedKind.VISIT, visitId);
    }
    /** MSG-80: reminder 24 hours before a booked appointment or a follow-up booked by the doctor. */
    public static NotificationMessage appointmentReminder(UUID appointmentId, Instant at, String patientName, boolean followUp) {
        return new NotificationMessage(followUp ? FOLLOW_UP_REMINDER : APPOINTMENT_REMINDER, "Nhắc lịch khám",
                "Nhắc lịch khám: " + patientName(patientName) + " có lịch " + (followUp ? "tái khám" : "khám lần đầu")
                + " vào " + HOUR.format(at) + " ngày " + DATE.format(at) + ".", NotificationRelatedKind.APPOINTMENT, appointmentId);
    }
    /** MSG-80 (overdue form): a recommended follow-up date has passed and the patient has not returned. */
    public static NotificationMessage followUpOverdue(UUID appointmentId, Instant at, String patientName) {
        return new NotificationMessage(FOLLOW_UP_OVERDUE, "Quá hạn tái khám", patientName(patientName)
                + " chưa quay lại theo hẹn ngày " + DATE.format(at) + ", vui lòng liên hệ phòng khám để đặt lịch.",
                NotificationRelatedKind.APPOINTMENT, appointmentId);
    }
    private static String patientName(String name) {
        return name == null || name.isBlank() ? "Bệnh nhân" : name.trim();
    }
    private static NotificationMessage appointment(String type, String title, String content, UUID id) {
        return new NotificationMessage(type, title, content, NotificationRelatedKind.APPOINTMENT, id);
    }
    private static String doctor(String name) {
        // Historical visits may lack a recorded doctor; never invent a name or reject the source operation.
        return name == null || name.isBlank() ? "" : " với bác sĩ " + name.trim();
    }
}
