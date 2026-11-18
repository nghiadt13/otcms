package fu.fa26.sep490.g58.otcms_be.clinical.service;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.LaboratoryResultValue;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DoctorLaboratoryResultService {
    List<Result> list(UUID visitId, UUID doctorId);
    Assessment assess(UUID visitId, UUID orderId, UUID doctorId, UUID accountId, String text);
    record Result(UUID orderId, String serviceName, List<LaboratoryResultValue> values, Assessment assessment) { }
    record Assessment(String text, UUID assessedByAccountId, Instant assessedAt) { }
}
