package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.ReceptionQueueResponse;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitProgressResponse;
import java.time.LocalDate;
import java.util.UUID;

public interface VisitReceptionReadService {
    ReceptionQueueResponse listWalkInQueue(UUID receptionistAccountId, LocalDate date, int page, int size);
    VisitProgressResponse getOwnProgress(UUID patientAccountId, UUID visitId);
}
