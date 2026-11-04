package fu.fa26.sep490.g58.otcms_be.clinical.model.entity;

import fu.fa26.sep490.g58.otcms_be.clinical.model.DiagnosisType;
import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="clinical_examination_diagnoses")
public class ClinicalDiagnosisJpaEntity {
    @Id private UUID id;
    @Column(name="examination_id",nullable=false) private UUID examinationId;
    @Column(nullable=false,length=8) private String code;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private DiagnosisType type;
    protected ClinicalDiagnosisJpaEntity(){}
    public ClinicalDiagnosisJpaEntity(UUID examinationId,String code,DiagnosisType type){id=UUID.randomUUID();this.examinationId=examinationId;this.code=code;this.type=type;}
    public UUID getId(){return id;} public UUID getExaminationId(){return examinationId;} public String getCode(){return code;} public DiagnosisType getType(){return type;}
}
