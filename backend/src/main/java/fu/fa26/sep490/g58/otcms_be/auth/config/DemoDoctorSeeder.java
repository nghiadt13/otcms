package fu.fa26.sep490.g58.otcms_be.auth.config;

import fu.fa26.sep490.g58.otcms_be.auth.model.entity.AccountJpaEntity;
import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.auth.repository.AccountRepository;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.entity.ClinicServiceJpaEntity;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.entity.DoctorServiceAssignmentJpaEntity;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.ClinicServiceRepository;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.repository.DoctorServiceAssignmentRepository;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.DoctorProfileJpaEntity;
import fu.fa26.sep490.g58.otcms_be.staff.model.entity.DoctorWorkShiftJpaEntity;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorProfileRepository;
import fu.fa26.sep490.g58.otcms_be.staff.repository.DoctorWorkShiftRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Demo-only seed for the booking flow while the doctor CRUD screens are unfinished: the clinic price
 * list as services, four doctors (account + profile), who performs which service, and work shifts for the
 * next {@value #SHIFT_DAYS} days. Idempotent: every row is looked up first, so restarts only add the
 * shifts of days that are not covered yet. Loaded only with the {@code demo} profile.
 */
@Configuration
@Profile("demo")
public class DemoDoctorSeeder {
    private static final int SHIFT_DAYS = 30;
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalTime MORNING_START = LocalTime.of(8, 0);
    private static final LocalTime MORNING_END = LocalTime.of(12, 0);
    private static final LocalTime AFTERNOON_START = LocalTime.of(13, 30);
    private static final LocalTime AFTERNOON_END = LocalTime.of(17, 30);

    private record ServiceSeed(String name, long priceVnd, int minutes, boolean onlineBookable) { }

    private record DoctorSeed(String email, String fullName, String specialties, String qualifications,
                              String biography, Set<DayOfWeek> morningDays, Set<DayOfWeek> afternoonDays,
                              List<String> services) { }

    private static final String EXAM_PROFESSOR = "Khám Nội – Ngoại khoa với Giáo sư – Phó Giáo sư";
    private static final String EXAM_DOCTOR_II = "Khám Nội – Ngoại khoa với Tiến sĩ – Bác sĩ chuyên khoa II";
    private static final String EXAM_MASTER = "Khám Nội – Ngoại khoa với Thạc sĩ – Bác sĩ Nội trú";

    /**
     * Bảng giá phòng khám (VND, cột `clinic_services.price`). Chỉ ba dịch vụ khám
     * đặt online được; chẩn đoán hình ảnh và xét nghiệm do bác sĩ chỉ định.
     */
    private static final List<ServiceSeed> SERVICES = List.of(
            new ServiceSeed(EXAM_PROFESSOR, 500_000L, 30, true),
            new ServiceSeed(EXAM_DOCTOR_II, 400_000L, 30, true),
            new ServiceSeed(EXAM_MASTER, 250_000L, 30, true),
            new ServiceSeed("Chụp cộng hưởng từ MRI", 1_800_000L, 45, false),
            new ServiceSeed("Chụp X-quang DR", 200_000L, 15, false),
            new ServiceSeed("Siêu âm màu 4D gói thường", 200_000L, 15, false),
            new ServiceSeed("Siêu âm màu 4D gói Doppler", 400_000L, 20, false),
            new ServiceSeed("Đo mật độ xương", 550_000L, 20, false),
            new ServiceSeed("Điện tim", 150_000L, 15, false),
            new ServiceSeed("Xét nghiệm Glucose và HbA1c", 170_000L, 15, false),
            new ServiceSeed("Xét nghiệm: GGT, GPT, GOT…", 90_000L, 15, false),
            new ServiceSeed("Xét nghiệm Beta HCG", 160_000L, 15, false));

    /** Dịch vụ mẫu của bản seed đầu (chưa có giá): xóa đi để danh mục chỉ còn bảng giá thật. */
    private static final Set<String> RETIRED_SERVICES = Set.of("Khám chỉnh hình tổng quát", "Khám cột sống",
            "Khám khớp và thể thao");

    private static final Set<DayOfWeek> MON_TO_SAT = Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);
    private static final Set<DayOfWeek> MON_WED_FRI = Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.FRIDAY);
    private static final Set<DayOfWeek> TUE_THU_SAT = Set.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY,
            DayOfWeek.SATURDAY);
    private static final Set<DayOfWeek> MON_TO_FRI = Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    private static final List<DoctorSeed> DOCTORS = List.of(
            new DoctorSeed("bs.quan@localhost", "BS. Nguyễn Minh Quân",
                    "Chấn thương chỉnh hình, khám tổng quát", "Thạc sĩ - Bác sĩ Nội trú",
                    "Hơn 12 năm điều trị chấn thương và bệnh lý xương khớp.",
                    MON_TO_SAT, MON_TO_SAT, List.of(EXAM_MASTER)),
            new DoctorSeed("bs.ha@localhost", "BS. Trần Thu Hà",
                    "Cột sống, thoát vị đĩa đệm", "Tiến sĩ - Bác sĩ chuyên khoa II",
                    "Chuyên sâu bệnh lý cột sống cổ và thắt lưng, điều trị bảo tồn.",
                    MON_WED_FRI, MON_WED_FRI, List.of(EXAM_DOCTOR_II)),
            new DoctorSeed("bs.nam@localhost", "BS. Lê Hoàng Nam",
                    "Khớp gối, chấn thương thể thao", "Phó Giáo sư - Tiến sĩ",
                    "Điều trị chấn thương khớp ở vận động viên và người chơi thể thao.",
                    TUE_THU_SAT, TUE_THU_SAT, List.of(EXAM_PROFESSOR, EXAM_DOCTOR_II)),
            new DoctorSeed("bs.anh@localhost", "BS. Phạm Ngọc Anh",
                    "Chỉnh hình nhi, khám tổng quát", "Bác sĩ chuyên khoa I",
                    "Khám và theo dõi phát triển xương khớp ở trẻ em.",
                    Set.of(), MON_TO_FRI, List.of(EXAM_MASTER)));

    @Bean
    public CommandLineRunner seedDemoDoctors(AccountRepository accounts, PasswordEncoder encoder,
            Environment environment, DoctorProfileRepository doctors, DoctorWorkShiftRepository shifts,
            ClinicServiceRepository services, DoctorServiceAssignmentRepository assignments,
            TransactionTemplate transaction) {
        return args -> {
            retireOldServices(services, assignments, transaction);
            transaction.executeWithoutResult(status -> {
            String password = environment.getProperty("app.demo.staff-password");
            if (password == null || password.length() < 8)
                throw new IllegalStateException("app.demo.staff-password (min 8 chars) is required for demo profile");

            List<ClinicServiceJpaEntity> catalog = SERVICES.stream()
                    .map(seed -> ensureService(services, seed)).toList();
            for (DoctorSeed seed : DOCTORS) {
                AccountJpaEntity account = DemoAccountSeeder.seed(accounts, encoder, seed.email(), password,
                        AccountType.DOCTOR);
                DoctorProfileJpaEntity doctor = doctors.findByAccountId(account.getId())
                        .orElseGet(() -> doctors.save(new DoctorProfileJpaEntity(UUID.randomUUID(), account.getId(),
                                seed.fullName(), null, seed.email(), seed.specialties(), seed.qualifications(),
                                seed.biography(), "Đặt lịch trước để được sắp xếp thời gian phù hợp.",
                                true, true, true)));
                for (ClinicServiceJpaEntity service : catalog) {
                    if (seed.services().contains(service.name()))
                        ensureAssignment(assignments, doctor.id(), service.id());
                }
                ensureShifts(shifts, doctor.id(), seed);
            }
            });
        };
    }

    /**
     * Xóa dịch vụ mẫu cũ cùng các gán bác sĩ của nó, mỗi dịch vụ một giao dịch riêng. Dịch vụ đã có lịch
     * hẹn tham chiếu không xóa được (khóa ngoại): giữ lại nhưng ẩn đi (`active = false`).
     */
    private static void retireOldServices(ClinicServiceRepository services,
            DoctorServiceAssignmentRepository assignments, TransactionTemplate transaction) {
        for (ClinicServiceJpaEntity service : services.findAll()) {
            if (!RETIRED_SERVICES.contains(service.name())) continue;
            try {
                transaction.executeWithoutResult(status -> {
                    assignments.deleteAll(assignments.findAll().stream()
                            .filter(assignment -> assignment.serviceId().equals(service.id())).toList());
                    services.delete(service);
                    services.flush();
                });
            } catch (RuntimeException exception) {
                if (!service.active()) continue;
                transaction.executeWithoutResult(status -> services.save(new ClinicServiceJpaEntity(service.id(),
                        service.name(), service.description(), service.price(), service.durationMinutes(),
                        service.bufferMinutes(), service.onlineBookable(), false)));
            }
        }
    }

    /** Tạo dịch vụ nếu chưa có; dịch vụ đã có thì cập nhật lại giá và các thông số theo bảng giá. */
    private static ClinicServiceJpaEntity ensureService(ClinicServiceRepository services, ServiceSeed seed) {
        BigDecimal price = BigDecimal.valueOf(seed.priceVnd()).setScale(2);
        UUID id = services.findAll().stream().filter(service -> service.name().equals(seed.name()))
                .map(ClinicServiceJpaEntity::id).findFirst().orElseGet(UUID::randomUUID);
        // save() là upsert theo id: cùng id thì ghi đè, nên chạy lại seed vẫn đưa giá về đúng bảng giá.
        return services.save(new ClinicServiceJpaEntity(id, seed.name(), null, price, seed.minutes(), 0,
                seed.onlineBookable(), true));
    }

    private static void ensureAssignment(DoctorServiceAssignmentRepository assignments, UUID doctorId,
            UUID serviceId) {
        if (assignments.existsByDoctorProfileIdAndServiceIdAndActiveTrue(doctorId, serviceId)) return;
        assignments.save(new DoctorServiceAssignmentJpaEntity(UUID.randomUUID(), doctorId, serviceId, true));
    }

    private static void ensureShifts(DoctorWorkShiftRepository shifts, UUID doctorId, DoctorSeed seed) {
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        for (int offset = 0; offset <= SHIFT_DAYS; offset++) {
            LocalDate day = today.plusDays(offset);
            if (seed.morningDays().contains(day.getDayOfWeek()))
                ensureShift(shifts, doctorId, day, MORNING_START, MORNING_END);
            if (seed.afternoonDays().contains(day.getDayOfWeek()))
                ensureShift(shifts, doctorId, day, AFTERNOON_START, AFTERNOON_END);
        }
    }

    private static void ensureShift(DoctorWorkShiftRepository shifts, UUID doctorId, LocalDate day,
            LocalTime from, LocalTime to) {
        var startsAt = day.atTime(from).atZone(CLINIC_ZONE).toInstant();
        var endsAt = day.atTime(to).atZone(CLINIC_ZONE).toInstant();
        if (shifts.existsByDoctorProfileIdAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(doctorId, startsAt,
                endsAt)) return;
        shifts.save(new DoctorWorkShiftJpaEntity(UUID.randomUUID(), doctorId, startsAt, endsAt));
    }
}
