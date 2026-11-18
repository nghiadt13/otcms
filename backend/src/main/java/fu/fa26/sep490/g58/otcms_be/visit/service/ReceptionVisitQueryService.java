package fu.fa26.sep490.g58.otcms_be.visit.service;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.ReceptionVisitPage;
import java.util.UUID;
public interface ReceptionVisitQueryService {
    ReceptionVisitPage list(AuthenticatedAccount actor, UUID patientId, String status, int page, int size);
}
