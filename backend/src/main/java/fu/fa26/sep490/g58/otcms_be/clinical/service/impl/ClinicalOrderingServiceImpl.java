package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException; import fu.fa26.sep490.g58.otcms_be.clinical.model.*; import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.*; import fu.fa26.sep490.g58.otcms_be.clinical.repository.*; import fu.fa26.sep490.g58.otcms_be.clinical.service.*;
import java.time.*; import java.util.*; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
@Service public class ClinicalOrderingServiceImpl implements ClinicalOrderingService {
 private final fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService guard;
 private final ClinicalOrderPreparationService preparation; private final ClinicalOrderingTransactionService writer; private final OrderingRoundRepository rounds; private final ServiceOrderRepository orders; private final Clock clock;
 public ClinicalOrderingServiceImpl(ClinicalOrderPreparationService preparation,ClinicalOrderingTransactionService writer,OrderingRoundRepository r,ServiceOrderRepository o,Clock clock,fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService guard){this.guard=guard;this.preparation=preparation;this.writer=writer;rounds=r;orders=o;this.clock=clock;}
 @Override @Transactional public SubmitRoundResult submit(SubmitRound c){
  if(c==null)throw new ClinicalOperationException("MSG-39");
  guard.acquire();
  var ctx=preparation.requireCurrentCare(c.visitId(),c.doctorId());
  var snapshots=preparation.prepare(c.orders(),false);
  var round=writer.createRound(c.visitId(),ctx.patientId(),c.doctorId(),snapshots);
  // Legacy compatibility fields only: Visit lifecycle is OPEN/CLOSED; coordination is separate.
  return new SubmitRoundResult(round,ClinicalVisitStatus.OTHER,false);
 }
 @Override @Transactional(readOnly=true) public OrderView getImagingOrder(UUID id){var o=orders.findById(id).orElseThrow(()->new ClinicalOperationException("MSG-04"));if(o.getServiceType()!=ClinicalServiceType.IMAGING||o.getOrderStatus()==ServiceOrderStatus.CANCELLED)throw new ClinicalOperationException("MSG-08");return view(o);}
 @Override @Transactional public void markImagingResultAvailable(UUID id){var o=orders.findById(id).orElseThrow(()->new ClinicalOperationException("MSG-04"));if(o.getServiceType()!=ClinicalServiceType.IMAGING)throw new ClinicalOperationException("MSG-08");try{o.markResult(clock.instant());}catch(IllegalStateException e){throw new ClinicalOperationException("MSG-08");}orders.save(o);}
 @Override @Transactional(readOnly=true) public boolean isLatestRoundReadyForReview(UUID visit){var r=rounds.findTopByVisitIdOrderByRoundNumberDesc(visit);if(r.isEmpty())return false;var effective=orders.findByOrderingRoundId(r.get().getId()).stream().filter(o->o.getOrderStatus()!=ServiceOrderStatus.CANCELLED).toList();return !effective.isEmpty()&&effective.stream().allMatch(o->o.getOrderStatus()==ServiceOrderStatus.RESULTS_AVAILABLE);}
 @Override @Transactional public OrderView cancel(UUID id,String reason){if(reason==null||reason.isBlank())throw new ClinicalOperationException("MSG-08");guard.acquire();var o=orders.findById(id).orElseThrow(()->new ClinicalOperationException("MSG-04"));try{o.cancel(reason,clock.instant());}catch(IllegalStateException e){throw new ClinicalOperationException("MSG-08");}return view(orders.save(o));}
 private OrderView view(ServiceOrderJpaEntity o){return new OrderView(o.getId(),o.getOrderCode(),o.getVisitId(),o.getPatientId(),o.getOrderingRoundId(),o.getServiceId(),o.getServiceNameSnapshot(),o.getServiceType(),o.getRequestingDoctorId(),o.getClinicalReason(),o.getBodyArea(),o.getInstructions(),o.getPriceSnapshot(),o.getOrderStatus(),o.getPaymentStatus());}
}
