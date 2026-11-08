package fu.fa26.sep490.g58.otcms_be.clinical.service;

import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderPaymentStatus;
import fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderStatus;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticOrderStatusQueryService.OrderDiagnosticStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ClinicalRoundWorkflowService {
    List<Order> listDoctorOrders(UUID visitId, UUID doctorProfileId);
    RoundPage listReceptionRounds(AuthenticatedAccount actor, int page, int size);
    List<RoomOptions> eligibleImagingRooms(List<UUID> serviceOrderIds, AuthenticatedAccount actor);
    List<UUID> dispatch(UUID roundId, AuthenticatedAccount actor, List<RoomAssignment> assignments);
    record RoomAssignment(@jakarta.validation.constraints.NotNull UUID serviceOrderId,
            @jakarta.validation.constraints.NotNull UUID roomId) { }
    record RoomOptions(UUID serviceOrderId,
            List<fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService.QueueRoom> items) { }
    record Order(UUID orderId, String orderCode, UUID orderingRoundId, int roundNumber, UUID serviceId,
            String serviceName, fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType serviceType, String clinicalReason, String bodyArea, BigDecimal priceSnapshot,
            ServiceOrderStatus orderStatus, ServiceOrderPaymentStatus paymentStatus, OrderDiagnosticStatus diagnostic) { }
    record Round(UUID roundId, UUID visitId, UUID patientProfileId, String patientName, int roundNumber,
            Instant orderedAt, List<Order> orders) { }
    record RoundPage(int page, int size, boolean hasMore, List<Round> items) { }
}
