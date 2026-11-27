package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitClinicalCareContextService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.ReceptionQueueItemResponse;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.ReceptionQueueResponse;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitProgressResponse;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitRoomJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitReceptionReadRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitReceptionReadService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisitReceptionReadServiceImpl implements VisitReceptionReadService {
    private static final int MAX_PAGE_SIZE = 100;
    private final VisitReceptionReadRepository reads;
    private final ReceptionistAccountAccessService receptionists;
    private final AuthorizePatientProfileService patientAuthorization;
    private final AppointmentBookingProperties booking;
    private final DoctorAccountAccessService doctors;
    private final DoctorProfileIdentityQueryService doctorProfiles;
    private final VisitClinicalCareContextService care;
    private final fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalVisitOrderQueryService orders;
    private final fu.fa26.sep490.g58.otcms_be.visit.repository.QueueRoomRepository roomQueues;
    private final VisitRoomJpaRepository rooms;

    public VisitReceptionReadServiceImpl(VisitReceptionReadRepository reads,
            ReceptionistAccountAccessService receptionists,
            AuthorizePatientProfileService patientAuthorization, AppointmentBookingProperties booking,
            DoctorAccountAccessService doctors, DoctorProfileIdentityQueryService doctorProfiles,
            VisitClinicalCareContextService care,
            fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalVisitOrderQueryService orders,
            fu.fa26.sep490.g58.otcms_be.visit.repository.QueueRoomRepository roomQueues,
            VisitRoomJpaRepository rooms) {
        this.reads = reads;
        this.receptionists = receptionists;
        this.patientAuthorization = patientAuthorization;
        this.booking = booking;
        this.doctors = doctors; this.doctorProfiles = doctorProfiles; this.care = care;
        this.orders = orders; this.roomQueues = roomQueues; this.rooms = rooms;
    }

    @Override
    @Transactional(readOnly = true)
    public VisitProgressResponse getProgress(AuthenticatedAccount actor, UUID visitId) {
        if (actor == null || actor.accountId() == null || actor.accountRole() == null) throw new VisitOperationException("MSG-40");
        if (visitId == null) throw new VisitOperationException("VALIDATION_FAILED");
        if (actor.accountRole() == AccountRole.PATIENT) return getOwnProgress(actor.accountId(), visitId);
        UUID expectedPatient = null;
        if (actor.accountRole() == AccountRole.RECEPTIONIST) {
            if (!receptionists.isActiveReceptionistAccount(actor.accountId())) throw new VisitOperationException("MSG-40");
        } else if (actor.accountRole() == AccountRole.DOCTOR) {
            if (!doctors.isActiveDoctorAccount(actor.accountId())) throw new VisitOperationException("MSG-40");
            var profile = doctorProfiles.findDoctorProfileId(actor.accountId()).orElseThrow(() -> new VisitOperationException("MSG-40"));
            var context = care.findClinicalCareContext(visitId).orElseThrow(() -> new VisitOperationException("MSG-40"));
            if (!visitId.equals(context.visitId()) || !context.clinicalWorkAllowed()
                    || !profile.equals(context.responsibleDoctorProfileId())) throw new VisitOperationException("MSG-40");
            expectedPatient = context.patientProfileId();
            if (expectedPatient == null) throw new VisitOperationException("MSG-40");
        } else throw new VisitOperationException("MSG-40");
        var row = reads.findProgress(visitId);
        if (row == null || !visitId.equals(row[0]) || expectedPatient != null && !expectedPatient.equals(row[1])) {
            throw new VisitOperationException("MSG-41");
        }
        return progress(row);
    }

    @Override
    @Transactional(readOnly = true)
    public ReceptionQueueResponse listWalkInQueue(UUID receptionistAccountId, LocalDate date, int page, int size) {
        if (date == null || page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new VisitOperationException("VALIDATION_FAILED");
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedAccount principal)
                || principal.accountRole() != AccountRole.RECEPTIONIST
                || receptionistAccountId == null || !receptionistAccountId.equals(principal.accountId())
                || !receptionists.isActiveReceptionistAccount(receptionistAccountId)) {
            throw new VisitOperationException("ACCESS_DENIED");
        }
        int offset;
        try {
            offset = Math.multiplyExact(page, size);
        } catch (ArithmeticException exception) {
            throw new VisitOperationException("VALIDATION_FAILED");
        }
        Timestamp from = Timestamp.from(date.atStartOfDay(booking.clinicTimeZone()).toInstant());
        Timestamp to = Timestamp.from(nextClinicDay(date).atStartOfDay(booking.clinicTimeZone()).toInstant());
        List<Object[]> rows = reads.findQueue(from, to, offset, size + 1);
        List<ReceptionQueueItemResponse> items = rows.stream()
                .limit(size).map(row -> new ReceptionQueueItemResponse((UUID) row[0], (UUID) row[1],
                        instant(row[2]), (String) row[3], ((Number) row[4]).intValue(), (String) row[5],
                        (String) row[6], row[7] == null ? null : ((Number) row[7]).intValue(), (UUID) row[8])).toList();
        return new ReceptionQueueResponse(date, page, size, rows.size() > size, items);
    }

    @Override
    @Transactional(readOnly = true)
    public VisitProgressResponse getOwnProgress(UUID patientAccountId, UUID visitId) {
        if (patientAccountId == null || visitId == null) throw new VisitOperationException("VALIDATION_FAILED");
        Object[] row = reads.findProgress(visitId);
        if (row == null) throw new VisitOperationException("MSG-41");
        try {
            patientAuthorization.authorize(patientAccountId, (UUID) row[1]);
        } catch (PatientAuthorizationDeniedException exception) {
            throw new VisitOperationException("MSG-40", exception);
        }
        return progress(row);
    }

    private VisitProgressResponse progress(Object[] row) {
        boolean activeWalkInEntry = "WALK_IN".equals(row[2]) && row[6] != null && "WAITING".equals(row[7]);
        Integer position = null;
        if (activeWalkInEntry) {
            LocalDate clinicDate = instant(row[4]).atZone(booking.clinicTimeZone()).toLocalDate();
            Timestamp from = Timestamp.from(clinicDate.atStartOfDay(booking.clinicTimeZone()).toInstant());
            Timestamp to = Timestamp.from(nextClinicDay(clinicDate).atStartOfDay(booking.clinicTimeZone()).toInstant());
            int currentPosition = reads.findPosition(asUuid(row[6]), from, to);
            if (currentPosition > 0) position = currentPosition;
        }
        UUID visitId = (UUID) row[0];
        Object[] active = reads.findActiveRoomQueue(visitId, booking.clinicTimeZone().getId());
        var roomsByOrder = roomQueues.findServicesForVisit(visitId).stream()
                .filter(q -> q.getServiceOrderId() != null && q.getRoomId() != null)
                .collect(java.util.stream.Collectors.toMap(q -> q.getServiceOrderId(), q -> q.getRoomId(), (a, b) -> a));
        // Hàng khám bệnh và hàng của từng chỉ định lấy riêng theo stage và service_order_id, không đoán từ hàng mới nhất.
        VisitProgressResponse.QueueInfo examQueue = null;
        Map<UUID, VisitProgressResponse.QueueInfo> queueByOrder = new java.util.HashMap<>();
        for (Object[] entry : reads.findActiveQueueEntries(visitId, booking.clinicTimeZone().getId())) {
            var info = new VisitProgressResponse.QueueInfo(
                    entry[2] == null ? null : ((Number) entry[2]).intValue(), (String) entry[3], (String) entry[4],
                    ((Number) entry[5]).intValue(), "CALLED".equals(entry[3]));
            if ("EXAM".equals(entry[0])) { if (examQueue == null) examQueue = info; }
            else if (entry[1] != null) queueByOrder.putIfAbsent(asUuid(entry[1]), info);
        }
        var orderRows = orders.findByVisit(visitId);
        var namesByRoom = roomNames(orderRows.stream()
                .map(order -> roomsByOrder.getOrDefault(order.id(), order.roomId())).toList());
        var serviceOrders = orderRows.stream().map(order -> {
            UUID roomId = roomsByOrder.getOrDefault(order.id(), order.roomId());
            return new VisitProgressResponse.ServiceOrder(order.id(), order.code(), order.serviceName(),
                    order.status(), order.paymentStatus(), roomId, roomId == null ? null : namesByRoom.get(roomId),
                    queueByOrder.get(order.id()));
        }).toList();
        Integer peopleAhead = null;
        if (active != null) peopleAhead = ((Number) active[3]).intValue();
        else if (position != null) peopleAhead = position - 1;
        Integer queueNumber = active == null || active[0] == null
                ? null : ((Number) active[0]).intValue();
        if (queueNumber == null && position != null) queueNumber = position;
        if (queueNumber == null && "OPEN".equals(row[3]) && row[10] instanceof String examStatus
                && java.util.Set.of("WAITING", "CALLED", "IN_EXAMINATION").contains(examStatus)) {
            queueNumber = row[9] == null ? null : ((Number) row[9]).intValue();
        }
        String currentRoom = active == null ? null : (String) active[2];
        if (currentRoom == null) currentRoom = (String) row[8];
        if (currentRoom == null) currentRoom = (String) row[11];
        return new VisitProgressResponse(visitId, (String) row[2], (String) row[3],
                (String) row[5], instant(row[4]), position, currentRoom, queueNumber,
                peopleAhead, active == null ? null : (String) active[4],
                active != null ? "CALLED".equals(active[1]) : "CALLED".equals(row[10]), serviceOrders, examQueue);
    }

    /** Tên phòng (nhãn hiển thị) theo mã phòng; không gọi bảng phòng khi chỉ định chưa có phòng nào. */
    private Map<UUID, String> roomNames(List<UUID> roomIds) {
        Set<UUID> ids = roomIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return rooms.findAllById(ids).stream()
                .collect(Collectors.toMap(VisitRoomJpaEntity::getId, VisitRoomJpaEntity::getDisplayLabel));
    }

    private static java.time.Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof java.time.Instant instant) return instant;
        if (value instanceof java.time.OffsetDateTime dateTime) return dateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalStateException("Unexpected database timestamp type");
    }

    private static UUID asUuid(Object value) {
        if (value instanceof UUID id) return id;
        if (value instanceof String id) return UUID.fromString(id);
        throw new IllegalStateException("Unexpected database UUID type");
    }

    private static LocalDate nextClinicDay(LocalDate date) {
        try {
            return date.plusDays(1);
        } catch (java.time.DateTimeException exception) {
            throw new VisitOperationException("VALIDATION_FAILED", exception);
        }
    }
}
