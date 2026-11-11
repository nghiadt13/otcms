package fu.fa26.sep490.g58.otcms_be.visit.mapper;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory.ProfileRecord;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.ExamQueueDtos;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

/**
 * Maps native exam-queue rows to response items. Column order is defined by
 * {@code VisitExamQueueJpaRepository.ROW_COLUMNS}.
 */
public final class ExamQueueItemMapper {
    private ExamQueueItemMapper() { }

    public static ExamQueueDtos.Item item(Object[] row, Map<UUID, ProfileRecord> patients,
            Map<UUID, String> doctorNames) {
        UUID patientProfileId = uuid(row[5]);
        UUID doctorProfileId = uuid(row[6]);
        return new ExamQueueDtos.Item(uuid(row[0]), integer(row[1]), (String) row[2], (String) row[3],
                uuid(row[4]), patientProfileId, patientName(patients.get(patientProfileId)), doctorProfileId,
                doctorNames.get(doctorProfileId), uuid(row[7]), (String) row[8], uuid(row[9]),
                instant(row[11]), instant(row[12]), instant(row[13]));
    }

    public static ExamQueueDtos.DoctorItem doctorItem(Object[] row, Map<UUID, ProfileRecord> patients,
            Map<UUID, String> doctorNames) {
        ExamQueueDtos.Item base = item(row, patients, doctorNames);
        ProfileRecord patient = patients.get(base.patientProfileId());
        return new ExamQueueDtos.DoctorItem(base.visitId(), base.queueNumber(), base.examStatus(), base.source(),
                base.appointmentId(), base.patientProfileId(), base.patientName(), base.doctorProfileId(),
                base.doctorName(), base.roomId(), base.roomLabel(), base.serviceId(), base.queuedAt(),
                base.startedAt(), base.finishedAt(),
                patient == null ? null : patient.dateOfBirth(),
                patient == null ? null : patient.gender());
    }

    /** Doctor name, then queue number (unnumbered rows last), then creation time, then visit id. */
    public static Comparator<Object[]> order(Map<UUID, String> doctorNames) {
        return Comparator
                .comparing((Object[] row) -> doctorNames.get(uuid(row[6])),
                        Comparator.nullsLast(Comparator.<String>naturalOrder()))
                .thenComparing(row -> integer(row[1]), Comparator.nullsLast(Comparator.<Integer>naturalOrder()))
                .thenComparing(row -> instant(row[10]), Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
                .thenComparing(row -> uuid(row[0]));
    }

    public static UUID patientProfileId(Object[] row) {
        return uuid(row[5]);
    }

    public static UUID doctorProfileId(Object[] row) {
        return uuid(row[6]);
    }

    private static String patientName(ProfileRecord patient) {
        return patient == null ? null : patient.fullName();
    }

    private static UUID uuid(Object value) {
        if (value == null) return null;
        if (value instanceof UUID id) return id;
        if (value instanceof String id) return UUID.fromString(id);
        throw new IllegalStateException("Unexpected database UUID type");
    }

    private static Integer integer(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalStateException("Unexpected database timestamp type");
    }
}
