package fu.fa26.sep490.g58.otcms_be.clinical.model.entity;

import fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalExaminationStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="clinical_examinations", uniqueConstraints=@UniqueConstraint(name="uk_clinical_exam_visit", columnNames="visit_id"))
public class ClinicalExaminationJpaEntity {
    @Id private UUID id;
    @Column(name="visit_id", nullable=false) private UUID visitId;
    @Column(name="reason_for_visit", columnDefinition="text") private String reasonForVisit;
    @Column(columnDefinition="text") private String symptoms;
    @Column(name="examination_findings", columnDefinition="text") private String examinationFindings;
    @Column(name="injury_body_site", length=120) private String injuryBodySite;
    @Column(name="injury_side", length=24) private String injurySide;
    @Column(name="injury_mechanism", columnDefinition="text") private String injuryMechanism;
    @Column(name="home_care_advice", columnDefinition="text") private String homeCareAdvice;
    @Column(name="activity_advice", columnDefinition="text") private String activityAdvice;
    @Column(name="rehabilitation_advice", columnDefinition="text") private String rehabilitationAdvice;
    @Column(name="warning_signs", columnDefinition="text") private String warningSigns;
    @Column(name="follow_up_recommendation", columnDefinition="text") private String followUpRecommendation;
    @Column(name="follow_up_indicated") private Boolean followUpIndicated;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=16) private ClinicalExaminationStatus status;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="updated_at", nullable=false) private Instant updatedAt;
    @Version private Long version;
    protected ClinicalExaminationJpaEntity() { }
    public static ClinicalExaminationJpaEntity create(UUID visitId, String reason, String symptoms, String findings,
            String site, String side, String mechanism, ClinicalExaminationStatus status, Instant now) {
        var e=new ClinicalExaminationJpaEntity(); e.id=UUID.randomUUID(); e.visitId=visitId; e.update(reason,symptoms,findings,site,side,mechanism,status,now); e.createdAt=now; return e;
    }
    public void update(String reason,String symptoms,String findings,String site,String side,String mechanism,ClinicalExaminationStatus status,Instant now) {
        reasonForVisit=reason; this.symptoms=symptoms; examinationFindings=findings; injuryBodySite=site; injurySide=side; injuryMechanism=mechanism; this.status=status; updatedAt=now;
    }
    public void updateAfterVisitAdvice(String homeCare, String activity, String rehabilitation,
            String warnings, String followUp, Instant now) {
        homeCareAdvice=homeCare;
        activityAdvice=activity;
        rehabilitationAdvice=rehabilitation;
        warningSigns=warnings;
        followUpRecommendation=followUp;
        updatedAt=now;
    }
    public UUID getId(){return id;} public UUID getVisitId(){return visitId;} public String getReasonForVisit(){return reasonForVisit;}
    public String getSymptoms(){return symptoms;} public String getExaminationFindings(){return examinationFindings;} public String getInjuryBodySite(){return injuryBodySite;}
    public String getInjurySide(){return injurySide;} public String getInjuryMechanism(){return injuryMechanism;} public ClinicalExaminationStatus getStatus(){return status;}
    public String getHomeCareAdvice(){return homeCareAdvice;} public String getActivityAdvice(){return activityAdvice;}
    public String getRehabilitationAdvice(){return rehabilitationAdvice;} public String getWarningSigns(){return warningSigns;}
    public String getFollowUpRecommendation(){return followUpRecommendation;}
    public Boolean getFollowUpIndicated(){return followUpIndicated;}
    public void setFollowUpIndicated(Boolean indicated){followUpIndicated=indicated;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public Long getVersion(){return version;}
}
