package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.auth.service.DoctorAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory;
import fu.fa26.sep490.g58.otcms_be.auth.service.PatientProfileDirectory.ProfileRecord;
import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService.AuthorizationScope;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService.AuthorizedPatientProfile;
import fu.fa26.sep490.g58.otcms_be.staff.model.DoctorWorkShift;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorProfileIdentityQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.DoctorScheduleService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService;
import fu.fa26.sep490.g58.otcms_be.staff.service.StaffAvailabilityProviderQueryService.AvailabilityProvider;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitExamStatus;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.ExamQueueDtos;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitRoomJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitExamQueueJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.VisitExamQueueReadServiceImpl;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class VisitExamQueueReadServiceImplTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant NOW = Instant.parse("2030-01-15T02:00:00Z"); // 09:00 in the clinic zone
    private static final LocalDate TODAY = LocalDate.of(2030, 1, 15);
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID DOCTOR_A = UUID.randomUUID();
    private static final UUID DOCTOR_B = UUID.randomUUID();
    private static final UUID DOCTOR_C = UUID.randomUUID();
    private static final UUID PATIENT_1 = UUID.randomUUID();
    private static final UUID PATIENT_2 = UUID.randomUUID();
    private static final UUID ROOM = UUID.randomUUID();
    private static final UUID RECEPTIONIST_ACCOUNT = UUID.randomUUID();
    private static final UUID INACTIVE_RECEPTIONIST_ACCOUNT = UUID.randomUUID();
    private static final UUID DOCTOR_A_ACCOUNT = UUID.randomUUID();
    private static final UUID DOCTOR_B_ACCOUNT = UUID.randomUUID();
    private static final UUID PATIENT_1_ACCOUNT = UUID.randomUUID();
    private static final UUID PATIENT_2_ACCOUNT = UUID.randomUUID();

    private final VisitExamQueueJpaRepository queue = mock(VisitExamQueueJpaRepository.class);
    private final VisitRoomJpaRepository rooms = mock(VisitRoomJpaRepository.class);
    private final PatientProfileDirectory patients = mock(PatientProfileDirectory.class);
    private final DoctorProfileIdentityQueryService doctorProfiles = mock(DoctorProfileIdentityQueryService.class);
    private final DoctorAccountAccessService doctorAccounts = mock(DoctorAccountAccessService.class);
    private final ReceptionistAccountAccessService receptionists = mock(ReceptionistAccountAccessService.class);
    private final AuthorizePatientProfileService patientAuthorization = mock(AuthorizePatientProfileService.class);
    private final StaffAvailabilityProviderQueryService providers = mock(StaffAvailabilityProviderQueryService.class);
    private final DoctorScheduleService schedules = mock(DoctorScheduleService.class);
    private final AppointmentBookingProperties booking = mock(AppointmentBookingProperties.class);
    private final VisitExamQueueReadServiceImpl service = new VisitExamQueueReadServiceImpl(queue, rooms, patients,
            doctorProfiles, doctorAccounts, receptionists, patientAuthorization, providers, schedules, booking, CLOCK);

    @BeforeEach
    void setUp() {
        when(booking.clinicTimeZone()).thenReturn(ZONE);
        when(patients.findProfiles(anyCollection())).thenReturn(List.of(
                new ProfileRecord(PATIENT_1, PATIENT_1_ACCOUNT, "Patient One", "+84900000001", "PATIENT",
                        LocalDate.of(1990, 5, 1), "FEMALE"),
                new ProfileRecord(PATIENT_2, PATIENT_2_ACCOUNT, "Patient Two", "+84900000002", "PATIENT",
                        LocalDate.of(1985, 2, 2), "MALE")));
        when(doctorProfiles.findDoctorNames(anyCollection())).thenReturn(
                Map.of(DOCTOR_A, "Dr A", DOCTOR_B, "Dr B", DOCTOR_C, "Dr C"));
    }

    @Test
    void receptionListOrdersByDoctorThenNumberWithUnnumberedRowsLast() {
        UUID first = UUID.randomUUID();
        UUID unnumbered = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID doctorBVisit = UUID.randomUUID();
        when(queue.findRowsForClinicDate(TODAY)).thenReturn(List.of(
                row(doctorBVisit, 2, "WAITING", PATIENT_1, DOCTOR_B, ROOM, "Phong 1", NOW),
                row(unnumbered, null, "PENDING_PAYMENT", PATIENT_2, DOCTOR_A, null, null, NOW.minusSeconds(60)),
                row(first, 1, "WAITING", PATIENT_1, DOCTOR_A, ROOM, "Phong 1", NOW),
                row(second, 3, "COMPLETED", PATIENT_2, DOCTOR_A, ROOM, "Phong 1", NOW)));

        List<ExamQueueDtos.Item> items = service.listForReception(TODAY).items();

        assertEquals(List.of(first, second, unnumbered, doctorBVisit),
                items.stream().map(ExamQueueDtos.Item::visitId).toList());
        assertEquals("Patient One", items.getFirst().patientName());
        assertEquals("Dr A", items.getFirst().doctorName());
        assertEquals("Phong 1", items.getFirst().roomLabel());
        assertNull(items.get(2).queueNumber());
        assertNull(items.get(2).roomLabel());
    }

    @Test
    void doctorListCarriesPatientDemographicsForTheDayAndUnfinishedRows() {
        UUID today = UUID.randomUUID();
        when(queue.findRowsForDoctorDay(DOCTOR_A, TODAY)).thenReturn(List.<Object[]>of(
                row(today, 1, "IN_EXAMINATION", PATIENT_1, DOCTOR_A, ROOM, "Phong 1", NOW)));

        ExamQueueDtos.DoctorItem item = service.listForDoctor(DOCTOR_A).items().getFirst();

        assertEquals(today, item.visitId());
        assertEquals(LocalDate.of(1990, 5, 1), item.dateOfBirth());
        assertEquals("FEMALE", item.gender());
        assertEquals("Patient One", item.patientName());
    }

    @Test
    void doctorAccountListNeedsAnActiveDoctorWithAProfile() {
        when(doctorAccounts.isActiveDoctorAccount(DOCTOR_A_ACCOUNT)).thenReturn(false);
        assertDenied(() -> service.listForDoctorAccount(actor(DOCTOR_A_ACCOUNT, AccountRole.DOCTOR)));

        when(doctorAccounts.isActiveDoctorAccount(DOCTOR_B_ACCOUNT)).thenReturn(true);
        when(doctorProfiles.findDoctorProfileId(DOCTOR_B_ACCOUNT)).thenReturn(Optional.empty());
        assertDenied(() -> service.listForDoctorAccount(actor(DOCTOR_B_ACCOUNT, AccountRole.DOCTOR)));
    }

    @Test
    void entryIsDeniedToOtherRolesAndTheWrongPatientOrDoctor() {
        UUID visit = UUID.randomUUID();
        when(queue.findRowByVisitId(visit)).thenReturn(List.of());
        assertErrorCode("RESOURCE_NOT_FOUND",
                () -> service.getEntry(visit, actor(RECEPTIONIST_ACCOUNT, AccountRole.RECEPTIONIST)));

        when(queue.findRowByVisitId(visit)).thenReturn(List.<Object[]>of(
                row(visit, 1, "WAITING", PATIENT_1, DOCTOR_A, ROOM, "Phong 1", NOW)));
        when(receptionists.isActiveReceptionistAccount(INACTIVE_RECEPTIONIST_ACCOUNT)).thenReturn(false);
        assertDenied(() -> service.getEntry(visit, actor(INACTIVE_RECEPTIONIST_ACCOUNT, AccountRole.RECEPTIONIST)));

        when(doctorAccounts.isActiveDoctorAccount(DOCTOR_B_ACCOUNT)).thenReturn(true);
        when(doctorProfiles.findDoctorProfileId(DOCTOR_B_ACCOUNT)).thenReturn(Optional.of(DOCTOR_B));
        assertDenied(() -> service.getEntry(visit, actor(DOCTOR_B_ACCOUNT, AccountRole.DOCTOR)));

        when(patientAuthorization.authorize(PATIENT_2_ACCOUNT, PATIENT_1))
                .thenThrow(new PatientAuthorizationDeniedException());
        assertDenied(() -> service.getEntry(visit, actor(PATIENT_2_ACCOUNT, AccountRole.PATIENT)));

        assertDenied(() -> service.getEntry(visit, actor(UUID.randomUUID(), AccountRole.ADMIN)));
    }

    @Test
    void entryIsReturnedToTheReceptionistAndToTheResponsibleDoctorAndPatient() {
        UUID visit = UUID.randomUUID();
        when(queue.findRowByVisitId(visit)).thenReturn(List.<Object[]>of(
                row(visit, 1, "WAITING", PATIENT_1, DOCTOR_A, ROOM, "Phong 1", NOW)));
        when(receptionists.isActiveReceptionistAccount(RECEPTIONIST_ACCOUNT)).thenReturn(true);
        when(doctorAccounts.isActiveDoctorAccount(DOCTOR_A_ACCOUNT)).thenReturn(true);
        when(doctorProfiles.findDoctorProfileId(DOCTOR_A_ACCOUNT)).thenReturn(Optional.of(DOCTOR_A));
        when(patientAuthorization.authorize(PATIENT_1_ACCOUNT, PATIENT_1))
                .thenReturn(new AuthorizedPatientProfile(PATIENT_1, AuthorizationScope.SELF));

        assertEquals(visit, service.getEntry(visit, actor(RECEPTIONIST_ACCOUNT, AccountRole.RECEPTIONIST)).visitId());
        assertEquals("Dr A", service.getEntry(visit, actor(DOCTOR_A_ACCOUNT, AccountRole.DOCTOR)).doctorName());
        assertEquals("Patient One", service.getEntry(visit, actor(PATIENT_1_ACCOUNT, AccountRole.PATIENT)).patientName());
    }

    @Test
    void onShiftDoctorsKeepsOnlyDoctorsWhoseShiftCoversNowAndCountsTheirWaiting() {
        when(providers.findAcceptingDoctorsInActiveDirectory()).thenReturn(List.of(
                new AvailabilityProvider(DOCTOR_C, "Dr C", true),
                new AvailabilityProvider(DOCTOR_A, "Dr A", true),
                new AvailabilityProvider(DOCTOR_B, "Dr B", true)));
        when(schedules.forProfile(DOCTOR_A, NOW, NOW.plusSeconds(1))).thenReturn(List.of(shift(DOCTOR_A, ROOM)));
        when(schedules.forProfile(DOCTOR_B, NOW, NOW.plusSeconds(1))).thenReturn(List.of());
        when(schedules.forProfile(DOCTOR_C, NOW, NOW.plusSeconds(1))).thenReturn(List.of(shift(DOCTOR_C, null)));
        VisitRoomJpaEntity room = mock(VisitRoomJpaEntity.class);
        when(room.getId()).thenReturn(ROOM);
        when(room.getDisplayLabel()).thenReturn("Phong 1");
        when(rooms.findAllById(List.of(ROOM))).thenReturn(List.of(room));
        when(queue.countExamRows(DOCTOR_A, TODAY, "WAITING"))
                .thenReturn(2L);
        when(queue.countExamRows(DOCTOR_C, TODAY, "WAITING"))
                .thenReturn(0L);

        List<ExamQueueDtos.OnShiftDoctor> doctors = service.onShiftDoctors(NOW).items();

        assertEquals(List.of(DOCTOR_A, DOCTOR_C),
                doctors.stream().map(ExamQueueDtos.OnShiftDoctor::doctorProfileId).toList());
        assertEquals(ROOM, doctors.getFirst().roomId());
        assertEquals("Phong 1", doctors.getFirst().roomLabel());
        assertEquals(2L, doctors.getFirst().waitingCount());
        assertNull(doctors.get(1).roomLabel());
        assertEquals(0L, doctors.get(1).waitingCount());
    }

    private static Object[] row(UUID visitId, Integer queueNumber, String status, UUID patientProfileId,
            UUID doctorProfileId, UUID roomId, String roomLabel, Instant createdAt) {
        return new Object[] {visitId, queueNumber, status, "WALK_IN", null, patientProfileId, doctorProfileId,
                roomId, roomLabel, null, createdAt, null, null, null};
    }

    private static DoctorWorkShift shift(UUID doctorProfileId, UUID roomId) {
        return new DoctorWorkShift(UUID.randomUUID(), doctorProfileId, NOW.minusSeconds(3600),
                NOW.plusSeconds(3600), roomId);
    }

    private static AuthenticatedAccount actor(UUID accountId, AccountRole role) {
        return new AuthenticatedAccount(accountId, role, UUID.randomUUID());
    }

    private static void assertDenied(Executable action) {
        assertErrorCode("ACCESS_DENIED", action);
    }

    private static void assertErrorCode(String code, Executable action) {
        VisitOperationException failure = assertThrows(VisitOperationException.class, action);
        assertEquals(code, failure.code());
    }
}
