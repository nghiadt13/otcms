package fu.fa26.sep490.g58.otcms_be.clinical.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.LaboratorySampleService.Sample;
import java.util.UUID;

public interface LaboratoryCollectionService {
    Sample collect(UUID orderId, boolean identityConfirmed, AuthenticatedAccount actor);
    Sample latest(UUID orderId, AuthenticatedAccount actor);
    java.util.List<fu.fa26.sep490.g58.otcms_be.diagnostic.model.LaboratoryResultValue> readDraft(UUID orderId, AuthenticatedAccount actor);
    java.util.List<fu.fa26.sep490.g58.otcms_be.diagnostic.model.LaboratoryResultValue> readResult(UUID orderId, AuthenticatedAccount actor);
    java.util.List<fu.fa26.sep490.g58.otcms_be.diagnostic.model.LaboratoryResultValue> saveDraft(UUID orderId, AuthenticatedAccount actor,
            java.util.List<fu.fa26.sep490.g58.otcms_be.diagnostic.model.LaboratoryResultValue> values);
    void approve(UUID orderId, AuthenticatedAccount actor, boolean checked, boolean critical);
}
