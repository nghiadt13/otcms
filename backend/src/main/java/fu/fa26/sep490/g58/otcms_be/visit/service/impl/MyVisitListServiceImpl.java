package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.patient.model.dto.DependentDtos.ProfileItem;
import fu.fa26.sep490.g58.otcms_be.patient.service.DependentProfileService;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientProfileAccessService;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.MyVisitItemResponse;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.MyVisitListService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Danh sách lượt của bệnh nhân (UC34). Phạm vi người khám dùng đúng kiểm quyền của tiến độ lượt (BR-19);
 * không trả kết quả khám (BR-35). Tài khoản chưa có hồ sơ nhận danh sách rỗng.
 */
@Service
public class MyVisitListServiceImpl implements MyVisitListService {
    private static final Duration RECENT_WINDOW = Duration.ofDays(7);
    private final ClinicVisitJpaRepository visits;
    private final PatientProfileAccessService access;
    private final DependentProfileService dependents;
    private final PatientProfileDirectory profiles;
    private final AppointmentBookingProperties booking;
    private final Clock clock;

    public MyVisitListServiceImpl(ClinicVisitJpaRepository visits, PatientProfileAccessService access,
            DependentProfileService dependents, PatientProfileDirectory profiles,
            AppointmentBookingProperties booking, Clock clock) {
        this.visits = visits;
        this.access = access;
        this.dependents = dependents;
        this.profiles = profiles;
        this.booking = booking;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MyVisitItemResponse> list(AuthenticatedAccount actor) {
        Set<UUID> scope = access.accessibleProfileIds(actor.accountId());
        if (scope.isEmpty()) return List.of();
        Map<UUID, String> names = new HashMap<>();
        profiles.findProfiles(scope).forEach(profile -> names.put(profile.id(), profile.fullName()));
        Map<UUID, String> relationships = relationships(actor.accountId(), scope);
        Timestamp since = Timestamp.from(clock.instant().minus(RECENT_WINDOW));
        return visits.findMyVisitRows(scope, since).stream()
                .map(row -> item(row, names, relationships))
                .toList();
    }

    /** Quan hệ của từng hồ sơ trong phạm vi, theo danh sách người khám đã xác nhận. */
    private Map<UUID, String> relationships(UUID accountId, Set<UUID> scope) {
        Map<UUID, String> relationships = new HashMap<>();
        for (ProfileItem item : dependents.list(accountId).items()) {
            if ("CONFIRMED".equals(item.linkStatus()) && scope.contains(item.patientProfileId())) {
                relationships.put(item.patientProfileId(), item.relationship());
            }
        }
        return relationships;
    }

    /** Cột theo thứ tự: id, patient_profile_id, checked_in_at, visit status, exam status, queue_number, room label. */
    private MyVisitItemResponse item(Object[] row, Map<UUID, String> names, Map<UUID, String> relationships) {
        UUID patientProfileId = (UUID) row[1];
        Instant checkedInAt = instant(row[2]);
        return new MyVisitItemResponse((UUID) row[0], patientProfileId, names.get(patientProfileId),
                relationships.get(patientProfileId), checkedInAt.atZone(booking.clinicTimeZone()).toLocalDate(),
                checkedInAt, status((String) row[3], (String) row[4]),
                row[5] == null ? null : ((Number) row[5]).intValue(), (String) row[6]);
    }

    /** Trạng thái R3 từ trạng thái lượt và trạng thái khám gần nhất. Chưa có Incomplete (thuộc phase 05). */
    private static String status(String visitStatus, String examStatus) {
        if ("CANCELLED".equals(examStatus)) return "CANCELLED_LEFT";
        if ("CLOSED".equals(visitStatus) || "COMPLETED".equals(examStatus)) return "COMPLETED";
        if ("IN_EXAMINATION".equals(examStatus)) return "IN_EXAMINATION";
        if ("AWAITING_CONCLUSION".equals(examStatus)) return "AWAITING_CONCLUSION";
        return "WAITING_FOR_EXAMINATION";
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalStateException("Unexpected database timestamp type");
    }
}
