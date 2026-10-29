package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.visit.model.VisitRoom;
import java.util.List;
import java.util.UUID;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.model.ClinicServiceType;

public interface ManageVisitRoomService {
    List<VisitRoom> listAll();
    VisitRoom get(UUID roomId);
    VisitRoom create(String roomCode, String displayLabel, ClinicServiceType type, String description,
            List<UUID> serviceIds, List<String> capabilities,
            UUID actorAccountId, String correlationId);
    VisitRoom update(UUID roomId, String displayLabel, String description,
            List<UUID> serviceIds, List<String> capabilities,
            UUID actorAccountId, String correlationId);
    void deactivate(UUID roomId, UUID actorAccountId, String correlationId);
    void activate(UUID roomId, UUID actorAccountId, String correlationId);
}
