package fu.fa26.sep490.g58.otcms_be.clinical.model.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DoctorActiveVisitDtos {
    private DoctorActiveVisitDtos() { }

    public record PatientIdentity(String fullName, LocalDate dateOfBirth, String gender) { }

    public record ActiveVisit(UUID visitId, UUID patientProfileId, PatientIdentity patient, String visitSource,
            UUID linkedAppointmentId, UUID assignmentId, String stepCode, Instant startedAt, Instant expectedEnd) { }

    public record ActiveVisits(List<ActiveVisit> items) {
        public ActiveVisits { items = List.copyOf(items); }
    }
}
