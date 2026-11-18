package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalOrderingService;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderQueueService;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationMessage;
import fu.fa26.sep490.g58.otcms_be.notification.model.NotificationRelatedKind;
import fu.fa26.sep490.g58.otcms_be.notification.service.NotificationPublisher;
import fu.fa26.sep490.g58.otcms_be.patient.service.PatientClinicalIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.ClinicVisitJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.QueueEntryJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitRoomJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ServiceQueueRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.ResultReviewQueueService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResultReviewQueueServiceImpl implements ResultReviewQueueService {
    private static final Logger log = LoggerFactory.getLogger(ResultReviewQueueServiceImpl.class);
    /** MSG-74 notification type. */
    public static final String NOTIFICATION_TYPE = "RESULT_REVIEW_READY";
    private final ClinicalServiceOrderQueueService orders;
    private final ClinicalOrderingService ordering;
    private final ServiceQueueRepository queue;
    private final ClinicVisitJpaRepository visits;
    private final VisitRoomJpaRepository rooms;
    private final PatientClinicalIdentityQueryService patients;
    private final NotificationPublisher notifications;
    private final AppointmentBookingProperties booking;
    private final Clock clock;

    public ResultReviewQueueServiceImpl(ClinicalServiceOrderQueueService orders, ClinicalOrderingService ordering,
            ServiceQueueRepository queue, ClinicVisitJpaRepository visits, VisitRoomJpaRepository rooms,
            PatientClinicalIdentityQueryService patients, NotificationPublisher notifications,
            AppointmentBookingProperties booking, Clock clock) {
        this.orders = orders;
        this.ordering = ordering;
        this.queue = queue;
        this.visits = visits;
        this.rooms = rooms;
        this.patients = patients;
        this.notifications = notifications;
        this.booking = booking;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void onOrderResultAvailable(UUID serviceOrderId) {
        var order = orders.find(serviceOrderId).orElse(null);
        if (order == null || !ordering.isLatestRoundReadyForReview(order.visitId())) return;
        ClinicVisitJpaEntity visit = visits.findByIdForUpdate(order.visitId()).orElse(null);
        if (visit == null || !"OPEN".equals(visit.getStatus())) return;
        if (!queue.findOpenResultReviewByVisit(visit.getId(), ServiceQueueRepository.OPEN).isEmpty()) return;
        // BR-29: the attending doctor's room is the room of the visit's exam entry (follows doctor transfers).
        QueueEntryJpaEntity exam = queue.findExamByVisit(visit.getId()).orElse(null);
        if (exam == null || exam.getRoomId() == null || "CANCELLED".equals(exam.getStatus())) {
            log.warn("Visit {} has all round results but no attending doctor room; no result-review entry", visit.getId());
            return;
        }
        VisitRoomJpaEntity room = rooms.findByIdForUpdate(exam.getRoomId()).orElse(null);
        if (room == null) return;
        Instant now = clock.instant();
        LocalDate day = now.atZone(booking.clinicTimeZone()).toLocalDate();
        int number = queue.maxNumber(room.getId(), QueueEntryJpaEntity.STAGE_RESULT_REVIEW, day) + 1;
        queue.save(QueueEntryJpaEntity.resultReview(visit.getId(), exam.getDoctorProfileId(), room.getId(), day, number, now));
        var identity = patients.findClinicalIdentities(List.of(visit.getPatientProfileId())).get(visit.getPatientProfileId());
        String name = identity == null || identity.fullName() == null ? "bệnh nhân" : identity.fullName();
        String roomLabel = room.getDisplayLabel() == null || room.getDisplayLabel().isBlank() ? room.getRoomCode() : room.getDisplayLabel();
        // MSG-74
        notifications.publishToPatient(visit.getPatientProfileId(), new NotificationMessage(NOTIFICATION_TYPE,
                "Đã có kết quả cận lâm sàng", "Đã có đủ kết quả cận lâm sàng của " + name + ". Vui lòng đến phòng "
                + roomLabel + " để bác sĩ đọc kết quả.", NotificationRelatedKind.VISIT, visit.getId()));
    }
}
