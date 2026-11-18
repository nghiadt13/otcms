package fu.fa26.sep490.g58.otcms_be.clinical.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LaboratoryQueueService {
    List<Item> today(AuthenticatedAccount actor);
    record Item(UUID id, String code, String patientName, java.time.LocalDate dateOfBirth, String serviceName, String clinicalReason,
            String paymentStatus, String orderStatus, String labStatus, Instant orderedAt, Instant paidAt) { }
}
