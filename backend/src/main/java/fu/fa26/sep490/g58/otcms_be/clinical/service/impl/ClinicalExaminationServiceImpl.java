package fu.fa26.sep490.g58.otcms_be.clinical.service.impl;
import fu.fa26.sep490.g58.otcms_be.clinical.exception.ClinicalOperationException; import fu.fa26.sep490.g58.otcms_be.clinical.model.*; import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.*; import fu.fa26.sep490.g58.otcms_be.clinical.repository.*; import fu.fa26.sep490.g58.otcms_be.clinical.service.*;
import java.time.Clock; import java.time.Instant; import java.util.*; import java.util.regex.Pattern; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
@Service public class ClinicalExaminationServiceImpl implements ClinicalExaminationService {
 public static final Pattern ICD=Pattern.compile("(?i)^[MS][0-9]{2}(\\.[A-Z0-9]{1,4})?$");
 private final fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService guard;
 private final VisitClinicalContextPort visits; private final ClinicalExaminationRepository exams; private final ClinicalDiagnosisRepository diagnoses; private final Clock clock;
 public ClinicalExaminationServiceImpl(VisitClinicalContextPort visits,ClinicalExaminationRepository exams,ClinicalDiagnosisRepository diagnoses,Clock clock,fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService guard){this.guard=guard;this.visits=visits;this.exams=exams;this.diagnoses=diagnoses;this.clock=clock;}
 @Override @Transactional public ExaminationView saveDraft(ExaminationCommand c){return save(c,ClinicalExaminationStatus.DRAFT);}
 @Override @Transactional public ExaminationView completeExamination(ExaminationCommand c){return save(c,ClinicalExaminationStatus.COMPLETED);}
 private ExaminationView save(ExaminationCommand c,ClinicalExaminationStatus status){
  if(c==null)throw new ClinicalOperationException("MSG-39");
  guard.acquire();
  var ctx=requireResponsibleDoctor(c.visitId(),c.doctorId());
  if(!ctx.clinicalWorkAllowed())throw new ClinicalOperationException("MSG-42");
  var ds=c.diagnoses()==null?List.<Diagnosis>of():c.diagnoses();
  boolean completed=status==ClinicalExaminationStatus.COMPLETED;
  if(completed){if(blank(c.reasonForVisit())||blank(c.symptoms())||blank(c.examinationFindings())||ds.stream().filter(d->d!=null&&d.type()==DiagnosisType.PRIMARY).count()!=1||ds.stream().anyMatch(d->d==null||d.type()==null||d.code()==null||!Icd10Catalog.contains(d.code())))throw new ClinicalOperationException("MSG-39");}
  ClinicalExamFieldRules.validate(c.injuryBodySite(),c.injurySide(),c.injuryMechanism(),c.injuryDescription(),c.treatmentDirections(),completed);
  var site=ClinicalExamFieldRules.trimToNull(c.injuryBodySite()); var side=ClinicalExamFieldRules.trimToNull(c.injurySide()); var mechanism=ClinicalExamFieldRules.trimToNull(c.injuryMechanism());
  Instant now=clock.instant();
  var existing=exams.findByVisitId(c.visitId());
  if(existing.isPresent()&&existing.get().getStatus()==ClinicalExaminationStatus.COMPLETED)throw new ClinicalOperationException("MSG-42");
  var e=existing.orElseGet(()->ClinicalExaminationJpaEntity.create(c.visitId(),c.reasonForVisit(),c.symptoms(),c.examinationFindings(),site,side,mechanism,status,now));
  e.update(c.reasonForVisit(),c.symptoms(),c.examinationFindings(),site,side,mechanism,status,now);
  e.setExamFields(ClinicalExamFieldRules.trimToNull(c.injuryDescription()),ClinicalExamFieldRules.normalizeDirections(c.treatmentDirections()));
  e=exams.save(e);
  diagnoses.deleteByExaminationId(e.getId());
  diagnoses.flush();
  for(var d:ds){if(d==null||d.type()==null||d.code()==null||!Icd10Catalog.contains(d.code()))throw new ClinicalOperationException("MSG-39");diagnoses.save(new ClinicalDiagnosisJpaEntity(e.getId(),d.code().toUpperCase(Locale.ROOT),d.type()));}
  return view(e,ds);
 }
 @Override @Transactional(readOnly=true) public ExaminationView getExamination(UUID visitId,UUID doctorId){requireResponsibleDoctor(visitId,doctorId);return exams.findByVisitId(visitId).map(e->view(e,diagnoses.findByExaminationId(e.getId()).stream().map(d->new Diagnosis(d.getCode(),d.getType())).toList())).orElseThrow(()->new ClinicalOperationException("MSG-41"));}
 private VisitClinicalContextPort.ClinicalVisitContext requireResponsibleDoctor(UUID visitId,UUID doctorId){
  if(visitId==null)throw new ClinicalOperationException("MSG-39");
  if(doctorId==null)throw new ClinicalOperationException("MSG-40");
  var ctx=visits.getVisitContext(visitId);
  if(ctx==null)throw new ClinicalOperationException("MSG-41");
  if(ctx.responsibleDoctorId()==null||!ctx.responsibleDoctorId().equals(doctorId))throw new ClinicalOperationException("MSG-40");
  return ctx;
 }
 private static boolean blank(String s){return s==null||s.isBlank();}
 private static ExaminationView view(ClinicalExaminationJpaEntity e,List<Diagnosis>d){return new ExaminationView(e.getId(),e.getVisitId(),e.getReasonForVisit(),e.getSymptoms(),e.getExaminationFindings(),e.getInjuryBodySite(),e.getInjurySide(),e.getInjuryMechanism(),e.getStatus(),List.copyOf(d),e.getVersion(),e.getInjuryDescription(),e.getTreatmentDirections()==null?null:List.of(e.getTreatmentDirections()));}
}
