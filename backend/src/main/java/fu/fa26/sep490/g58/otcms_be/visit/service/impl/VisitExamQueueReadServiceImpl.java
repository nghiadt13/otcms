package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory.ProfileRecord;
import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorWorkShift;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorScheduleService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService.AvailabilityProvider;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.mapper.ExamQueueItemMapper;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.ExamQueueDtos;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitRoomJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitExamQueueJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitExamQueueReadService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VisitExamQueueReadServiceImpl implements VisitExamQueueReadService {
    private final VisitExamQueueJpaRepository queue;
    private final VisitRoomJpaRepository rooms;
    private final PatientProfileDirectory patients;
    private final DoctorProfileIdentityQueryService doctorProfiles;
    private final DoctorAccountAccessService doctorAccounts;
    private final ReceptionistAccountAccessService receptionists;
    private final AuthorizePatientProfileService patientAuthorization;
    private final StaffAvailabilityProviderQueryService providers;
    private final DoctorScheduleService schedules;
    private final AppointmentBookingProperties booking;
    private final Clock clock;

    public VisitExamQueueReadServiceImpl(VisitExamQueueJpaRepository queue, VisitRoomJpaRepository rooms,
            PatientProfileDirectory patients, DoctorProfileIdentityQueryService doctorProfiles,
            DoctorAccountAccessService doctorAccounts, ReceptionistAccountAccessService receptionists,
            AuthorizePatientProfileService patientAuthorization, StaffAvailabilityProviderQueryService providers,
            DoctorScheduleService schedules, AppointmentBookingProperties booking, Clock clock) {
        this.queue = queue;
        this.rooms = rooms;
        this.patients = patients;
        this.doctorProfiles = doctorProfiles;
        this.doctorAccounts = doctorAccounts;
        this.receptionists = receptionists;
        this.patientAuthorization = patientAuthorization;
        this.providers = providers;
        this.schedules = schedules;
        this.booking = booking;
        this.clock = clock;
    }

    @Override
    public ExamQueueDtos.ItemList listForReception(LocalDate clinicDate) {
        return new ExamQueueDtos.ItemList(items(queue.findRowsForClinicDate(clinicDate)));
    }

    @Override
    public ExamQueueDtos.DoctorItemList listForDoctor(UUID doctorProfileId) {
        LocalDate today = LocalDate.now(clock.withZone(booking.clinicTimeZone()));
        List<Object[]> rows = queue.findRowsForDoctorDay(doctorProfileId, today);
        Map<UUID, ProfileRecord> patientsById = patientsOf(rows);
        Map<UUID, String> names = doctorNamesOf(rows);
        return new ExamQueueDtos.DoctorItemList(rows.stream().sorted(ExamQueueItemMapper.order(names))
                .map(row -> ExamQueueItemMapper.doctorItem(row, patientsById, names)).toList());
    }

    @Override
    public ExamQueueDtos.DoctorItemList listForDoctorAccount(AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null || !doctorAccounts.isActiveDoctorAccount(actor.accountId())) {
            throw denied();
        }
        UUID doctorProfileId = doctorProfiles.findDoctorProfileId(actor.accountId())
                .orElseThrow(VisitExamQueueReadServiceImpl::denied);
        return listForDoctor(doctorProfileId);
    }

    @Override
    public ExamQueueDtos.OnShiftDoctorList onShiftDoctors(Instant now) {
        LocalDate today = now.atZone(booking.clinicTimeZone()).toLocalDate();
        Instant until = now.plusSeconds(1);
        List<OnShiftCandidate> candidates = new ArrayList<>();
        for (AvailabilityProvider provider : providers.findAcceptingDoctorsInActiveDirectory()) {
            schedules.forProfile(provider.doctorProfileId(), now, until).stream()
                    .min(Comparator.comparing(DoctorWorkShift::startsAt))
                    .ifPresent(shift -> candidates.add(new OnShiftCandidate(provider.doctorProfileId(),
                            provider.displayName(), shift.roomId())));
        }
        Map<UUID, String> roomLabels = roomLabels(candidates);
        return new ExamQueueDtos.OnShiftDoctorList(candidates.stream()
                .sorted(Comparator.comparing(OnShiftCandidate::doctorName,
                                Comparator.nullsLast(Comparator.<String>naturalOrder()))
                        .thenComparing(OnShiftCandidate::doctorProfileId))
                .map(candidate -> new ExamQueueDtos.OnShiftDoctor(candidate.doctorProfileId(), candidate.doctorName(),
                        candidate.roomId(), candidate.roomId() == null ? null : roomLabels.get(candidate.roomId()),
                        queue.countExamRows(candidate.doctorProfileId(), today,
                                VisitExamStatus.WAITING.name())))
                .toList());
    }

    @Override
    public ExamQueueDtos.DoctorItem getEntry(UUID visitId, AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null || actor.accountRole() == null) {
            throw denied();
        }
        List<Object[]> rows = queue.findRowByVisitId(visitId);
        if (rows.isEmpty()) {
            throw new VisitOperationException("RESOURCE_NOT_FOUND");
        }
        authorizeEntry(actor, rows.getFirst());
        return ExamQueueItemMapper.doctorItem(rows.getFirst(), patientsOf(rows), doctorNamesOf(rows));
    }

    private void authorizeEntry(AuthenticatedAccount actor, Object[] row) {
        UUID accountId = actor.accountId();
        switch (actor.accountRole()) {
            case RECEPTIONIST -> {
                if (!receptionists.isActiveReceptionistAccount(accountId)) {
                    throw denied();
                }
            }
            case DOCTOR -> {
                UUID responsibleProfileId = ExamQueueItemMapper.doctorProfileId(row);
                if (!doctorAccounts.isActiveDoctorAccount(accountId)
                        || !responsibleProfileId.equals(doctorProfiles.findDoctorProfileId(accountId).orElse(null))) {
                    throw denied();
                }
            }
            case PATIENT -> {
                try {
                    patientAuthorization.authorize(accountId, ExamQueueItemMapper.patientProfileId(row));
                } catch (PatientAuthorizationDeniedException exception) {
                    throw new VisitOperationException("ACCESS_DENIED", exception);
                }
            }
            default -> throw denied();
        }
    }

    private List<ExamQueueDtos.Item> items(List<Object[]> rows) {
        Map<UUID, ProfileRecord> patientsById = patientsOf(rows);
        Map<UUID, String> names = doctorNamesOf(rows);
        return rows.stream().sorted(ExamQueueItemMapper.order(names))
                .map(row -> ExamQueueItemMapper.item(row, patientsById, names)).toList();
    }

    private Map<UUID, ProfileRecord> patientsOf(List<Object[]> rows) {
        List<UUID> ids = rows.stream().map(ExamQueueItemMapper::patientProfileId).distinct().toList();
        return patients.findProfiles(ids).stream().collect(Collectors.toMap(ProfileRecord::id, Function.identity()));
    }

    private Map<UUID, String> doctorNamesOf(List<Object[]> rows) {
        return doctorProfiles.findDoctorNames(
                rows.stream().map(ExamQueueItemMapper::doctorProfileId).distinct().toList());
    }

    private Map<UUID, String> roomLabels(List<OnShiftCandidate> candidates) {
        List<UUID> roomIds = candidates.stream().map(OnShiftCandidate::roomId).filter(Objects::nonNull)
                .distinct().toList();
        return rooms.findAllById(roomIds).stream()
                .collect(Collectors.toMap(VisitRoomJpaEntity::getId, VisitRoomJpaEntity::getDisplayLabel));
    }

    private static VisitOperationException denied() {
        return new VisitOperationException("ACCESS_DENIED");
    }

    private record OnShiftCandidate(UUID doctorProfileId, String doctorName, UUID roomId) {
    }
}
