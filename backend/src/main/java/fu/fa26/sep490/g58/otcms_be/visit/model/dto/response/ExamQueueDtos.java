package fu.fa26.sep490.g58.otcms_be.visit.model.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Response bodies of the exam-queue read APIs (plans/261111-0208-visit-flow-r3, section 3, A1-A3 and A6). */
public final class ExamQueueDtos {
    private ExamQueueDtos() { }

    /** One queue row for reception, or the single entry of a visit (A1, A6). */
    public record Item(UUID visitId, Integer queueNumber, String examStatus, String source, UUID appointmentId,
            UUID patientProfileId, String patientName, UUID doctorProfileId, String doctorName, UUID roomId,
            String roomLabel, UUID serviceId, Instant queuedAt, Instant startedAt, Instant finishedAt) {
    }

    public record ItemList(List<Item> items) {
    }

    /**
     * One queue row for the responsible doctor (A2) and the single-entry read (A6): the reception fields plus the
     * patient's date of birth and gender.
     */
    public record DoctorItem(UUID visitId, Integer queueNumber, String examStatus, String source, UUID appointmentId,
            UUID patientProfileId, String patientName, UUID doctorProfileId, String doctorName, UUID roomId,
            String roomLabel, UUID serviceId, Instant queuedAt, Instant startedAt, Instant finishedAt,
            LocalDate dateOfBirth, String gender) {
    }

    public record DoctorItemList(List<DoctorItem> items) {
    }

    /** A doctor on shift now (A3). */
    public record OnShiftDoctor(UUID doctorProfileId, String doctorName, UUID roomId, String roomLabel,
            long waitingCount) {
    }

    public record OnShiftDoctorList(List<OnShiftDoctor> items) {
    }
}
