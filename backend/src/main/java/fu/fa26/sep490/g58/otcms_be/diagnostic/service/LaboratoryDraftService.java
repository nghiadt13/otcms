package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.LaboratoryResultValue;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Storage of unissued laboratory transcription; authorization is performed by the caller. */
public interface LaboratoryDraftService {
    List<LaboratoryResultValue> read(UUID orderId);
    List<LaboratoryResultValue> save(UUID orderId, UUID visitId, UUID sampleId, UUID actorId, List<LaboratoryResultValue> values, Instant now);
    void attach(UUID orderId, UUID actorId, byte[] content, String contentType, Instant receivedAt, Instant now);
    ReportInfo reportInfo(UUID orderId);
    IssuedReportInfo issuedReportInfo(UUID orderId);
    java.io.InputStream openReport(UUID orderId);
    void approve(UUID orderId, UUID actorId, Instant now);
    List<LaboratoryResultValue> issued(UUID orderId);
    record ReportInfo(Instant receivedAt, String contentType) { }
    record IssuedReportInfo(UUID reportId, int version, Instant releasedAt, String contentType) { }
}
