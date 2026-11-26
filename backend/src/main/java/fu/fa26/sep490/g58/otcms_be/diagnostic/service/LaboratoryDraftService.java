package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.LaboratoryResultValue;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Storage of unissued laboratory transcription; authorization is performed by the caller. */
public interface LaboratoryDraftService {
    List<LaboratoryResultValue> read(UUID orderId);
    List<LaboratoryResultValue> initialize(UUID orderId, UUID visitId, UUID sampleId, UUID actorId,
            List<LaboratoryResultValue> configuredValues, Instant now);
    List<LaboratoryResultValue> save(UUID orderId, UUID visitId, UUID sampleId, UUID actorId, List<LaboratoryResultValue> values, Instant now);
    void approve(UUID orderId, UUID actorId, Instant now);
    List<LaboratoryResultValue> issued(UUID orderId);
}
