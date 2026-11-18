package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalServiceOrderQueueService;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorWorkShift;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorScheduleService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitRoom;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ServiceQueueRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService;
import fu.fa26.sep490.g58.otcms_be.visit.service.ManageVisitRoomService;
import fu.fa26.sep490.g58.otcms_be.visit.service.StaffRoomScopeService;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StaffRoomScopeServiceImpl implements StaffRoomScopeService {
    private final ManageVisitRoomService rooms;
    private final DoctorScheduleService schedules;
    private final DoctorProfileIdentityQueryService doctors;
    private final ServiceQueueRepository queue;
    private final ClinicalServiceOrderQueueService orders;
    private final ImagingRoomQueueService imagingRooms;
    private final AppointmentBookingProperties booking;
    private final Clock clock;

    public StaffRoomScopeServiceImpl(ManageVisitRoomService rooms, DoctorScheduleService schedules,
            DoctorProfileIdentityQueryService doctors, ServiceQueueRepository queue,
            ClinicalServiceOrderQueueService orders, ImagingRoomQueueService imagingRooms,
            AppointmentBookingProperties booking, Clock clock) {
        this.rooms = rooms;
        this.schedules = schedules;
        this.doctors = doctors;
        this.queue = queue;
        this.orders = orders;
        this.imagingRooms = imagingRooms;
        this.booking = booking;
        this.clock = clock;
    }

    @Override
    public List<VisitRoom> listVisible(AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null || actor.accountRole() == null) throw new VisitOperationException("MSG-40");
        List<VisitRoom> all = rooms.listAll();
        return switch (actor.accountRole()) {
            case ADMIN, OWNER -> all;
            case RECEPTIONIST -> all.stream().filter(VisitRoom::active).toList();
            case DOCTOR -> {
                Set<UUID> allowed = doctorRoomIds(actor.accountId());
                yield all.stream().filter(room -> allowed.contains(room.id())).toList();
            }
            case IMAGING_TECHNICIAN -> all.stream().filter(VisitRoom::active)
                    .filter(room -> imagingRooms.isAssignedTechnician(actor.accountId(), room.id())).toList();
            case LAB_TECHNICIAN -> all.stream().filter(VisitRoom::active)
                    .filter(room -> "LABORATORY".equals(room.type())).toList();
            default -> throw new VisitOperationException("MSG-40");
        };
    }

    @Override
    public VisitRoom getVisible(AuthenticatedAccount actor, UUID roomId) {
        if (roomId == null) throw new VisitOperationException("MSG-50");
        return listVisible(actor).stream().filter(room -> room.id().equals(roomId)).findFirst()
                .orElseThrow(() -> new VisitOperationException("MSG-50"));
    }

    /** Rooms of today's shifts plus rooms of service orders of visits in the doctor's own exam queue. */
    private Set<UUID> doctorRoomIds(UUID accountId) {
        Set<UUID> result = new HashSet<>();
        Instant from = clock.instant().atZone(booking.clinicTimeZone()).toLocalDate()
                .atStartOfDay(booking.clinicTimeZone()).toInstant();
        for (DoctorWorkShift shift : schedules.forAccount(accountId, from, from.plusSeconds(24 * 3600L))) {
            if (shift.getRoomId() != null) result.add(shift.getRoomId());
        }
        doctors.findDoctorProfileId(accountId).ifPresent(profileId -> {
            for (UUID visitId : queue.findOpenExamVisitIds(profileId)) {
                orders.findByVisit(visitId).stream().map(ClinicalServiceOrderQueueService.QueueOrder::roomId)
                        .filter(java.util.Objects::nonNull).forEach(result::add);
            }
        });
        return result;
    }
}
