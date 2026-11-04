package fu.fa26.sep490.g58.otcms_be.clinical.service;
import fu.fa26.sep490.g58.otcms_be.clinical.model.*; import java.math.BigDecimal; import java.time.Instant; import java.util.*;
public interface ClinicalOrderingService {
 record OrderRequest(UUID serviceId,String clinicalReason,String bodyArea,String instructions) { }
 record SubmitRound(UUID visitId,UUID doctorId,List<OrderRequest> orders) { }
 record OrderView(UUID orderId,String orderCode,UUID visitId,UUID patientId,UUID orderingRoundId,UUID serviceId,String serviceName,ClinicalServiceType serviceType,UUID requestingDoctorId,String clinicalReason,String bodyArea,String instructions,BigDecimal priceSnapshot,ServiceOrderStatus orderStatus,ServiceOrderPaymentStatus paymentStatus) { }
 record RoundView(UUID id,UUID visitId,int roundNumber,UUID orderedByDoctorId,Instant orderedAt,List<OrderView> orders) { }
 record SubmitRoundResult(RoundView round, ClinicalVisitStatus previousVisitStatus, boolean requiresAwaitingConclusionTransition) { }
 SubmitRoundResult submit(SubmitRound command); OrderView getImagingOrder(UUID orderId); void markImagingResultAvailable(UUID orderId); boolean isLatestRoundReadyForReview(UUID visitId); OrderView cancel(UUID orderId,String reason);
}
