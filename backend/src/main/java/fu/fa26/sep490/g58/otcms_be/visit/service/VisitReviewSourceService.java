package fu.fa26.sep490.g58.otcms_be.visit.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Visit-owned projection; consumers never access visit persistence directly. */
public interface VisitReviewSourceService {
    record Source(UUID id, UUID patientProfileId, String status, Instant arrivedAt,
            String doctorName, String serviceName, UUID originatingAppointmentId, UUID doctorProfileId) {
        public Source(UUID id, UUID patientProfileId, String status, Instant arrivedAt, String doctorName, String serviceName) {
            this(id, patientProfileId, status, arrivedAt, doctorName, serviceName, null, null);
        }
    }
    Optional<Source> find(UUID visitId);
    List<Source> closedVisits(UUID patientProfileId);
}
