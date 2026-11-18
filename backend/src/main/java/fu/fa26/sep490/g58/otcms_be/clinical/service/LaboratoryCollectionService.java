package fu.fa26.sep490.g58.otcms_be.clinical.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.LaboratorySampleService.Sample;
import java.util.UUID;

public interface LaboratoryCollectionService {
    Sample collect(UUID orderId, boolean identityConfirmed, AuthenticatedAccount actor);
    Sample latest(UUID orderId, AuthenticatedAccount actor);
    java.util.List<fu.fa26.sep490.g58.otcms_be.diagnostic.model.LaboratoryResultValue> readDraft(UUID orderId, AuthenticatedAccount actor);
    java.util.List<fu.fa26.sep490.g58.otcms_be.diagnostic.model.LaboratoryResultValue> saveDraft(UUID orderId, AuthenticatedAccount actor,
            java.util.List<fu.fa26.sep490.g58.otcms_be.diagnostic.model.LaboratoryResultValue> values);
    void attachReport(UUID orderId, AuthenticatedAccount actor, byte[] content, String contentType, java.time.Instant receivedAt);
    fu.fa26.sep490.g58.otcms_be.diagnostic.service.LaboratoryDraftService.ReportInfo reportInfo(UUID orderId, AuthenticatedAccount actor);
    java.io.InputStream openReport(UUID orderId, AuthenticatedAccount actor);
    void approve(UUID orderId, AuthenticatedAccount actor, boolean checked, boolean critical, String contactedDoctor, java.time.Instant contactedAt);
}
