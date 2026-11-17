package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorWorkShift;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorScheduleService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitLifecycleDtos.WalkInSuggestion;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitLifecycleDtos.WalkInSuggestions;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitRoomJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitLifecycleRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.WalkInRoomSuggestionService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WalkInRoomSuggestionServiceImpl implements WalkInRoomSuggestionService {
    private final ReceptionistAccountAccessService receptionists;
    private final StaffAvailabilityProviderQueryService providers;
    private final DoctorScheduleService schedules;
    private final VisitRoomJpaRepository rooms;
    private final VisitLifecycleRepository lifecycle;
    private final AppointmentBookingProperties booking;
    private final Clock clock;

    public WalkInRoomSuggestionServiceImpl(ReceptionistAccountAccessService receptionists,
            StaffAvailabilityProviderQueryService providers, DoctorScheduleService schedules,
            VisitRoomJpaRepository rooms, VisitLifecycleRepository lifecycle, AppointmentBookingProperties booking,
            Clock clock) {
        this.receptionists = receptionists;
        this.providers = providers;
        this.schedules = schedules;
        this.rooms = rooms;
        this.lifecycle = lifecycle;
        this.booking = booking;
        this.clock = clock;
    }

    @Override
    public WalkInSuggestions suggest(AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null || actor.accountRole() != AccountRole.RECEPTIONIST
                || !receptionists.isActiveReceptionistAccount(actor.accountId())) {
            throw new VisitOperationException("MSG-40");
        }
        Instant now = clock.instant();
        record OnDuty(UUID doctorProfileId, String doctorName, UUID roomId) { }
        List<OnDuty> onDuty = new ArrayList<>();
        for (var provider : providers.findAcceptingDoctorsInActiveDirectory()) {
            List<DoctorWorkShift> shifts;
            try {
                shifts = schedules.forProfile(provider.doctorProfileId(), now, now.plusSeconds(1));
            } catch (fu.fa26.sep490.g58.otcms_be.staff.exception.DoctorProfileException unknownDoctor) {
                continue;
            }
            shifts.stream().filter(shift -> shift.roomId() != null).min(Comparator.comparing(DoctorWorkShift::startsAt))
                    .ifPresent(shift -> onDuty.add(new OnDuty(provider.doctorProfileId(), provider.displayName(),
                            shift.roomId())));
        }
        if (onDuty.isEmpty()) return new WalkInSuggestions(List.of());
        Map<UUID, VisitRoomJpaEntity> usable = new HashMap<>();
        rooms.findAllById(onDuty.stream().map(OnDuty::roomId).distinct().toList()).stream()
                .filter(room -> room.isActive() && "EXAMINATION".equals(room.getType()))
                .forEach(room -> usable.put(room.getId(), room));
        var today = now.atZone(booking.clinicTimeZone()).toLocalDate();
        Map<UUID, Long> waiting = lifecycle.countWaitingByRoom(usable.keySet(), today);
        List<WalkInSuggestion> items = onDuty.stream().filter(entry -> usable.containsKey(entry.roomId()))
                .map(entry -> new WalkInSuggestion(entry.roomId(), usable.get(entry.roomId()).getRoomCode(),
                        usable.get(entry.roomId()).getDisplayLabel(), entry.doctorProfileId(), entry.doctorName(),
                        waiting.getOrDefault(entry.roomId(), 0L)))
                .sorted(Comparator.comparingLong(WalkInSuggestion::waitingCount)
                        .thenComparing(WalkInSuggestion::roomCode).thenComparing(item -> item.doctorProfileId().toString()))
                .toList();
        return new WalkInSuggestions(items);
    }
}
