package fu.fa26.sep490.g58.otcms_be.clinical.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import java.util.UUID;
import java.util.List;
import fu.fa26.sep490.g58.otcms_be.visit.service.LaboratoryRoomQueryService;

public interface ClinicalLaboratoryDispatchService {
    void assign(UUID orderId, UUID roomId, AuthenticatedAccount actor);
    List<LaboratoryRoomQueryService.Room> eligibleRooms(UUID orderId, AuthenticatedAccount actor);
}
