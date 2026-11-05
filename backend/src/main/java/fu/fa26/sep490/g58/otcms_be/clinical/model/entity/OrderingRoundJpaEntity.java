package fu.fa26.sep490.g58.otcms_be.clinical.model.entity;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="clinical_ordering_rounds",uniqueConstraints=@UniqueConstraint(name="uk_clinical_round_visit_number",columnNames={"visit_id","round_number"}))
public class OrderingRoundJpaEntity {
 @Id private UUID id; @Column(name="visit_id",nullable=false) private UUID visitId; @Column(name="patient_id",nullable=false) private UUID patientId;
 @Column(name="round_number",nullable=false) private int roundNumber; @Column(name="ordered_by_doctor_id",nullable=false) private UUID orderedByDoctorId;
 @Column(name="ordered_at",nullable=false) private Instant orderedAt; @Column(name="created_at",nullable=false) private Instant createdAt;
 protected OrderingRoundJpaEntity(){} public OrderingRoundJpaEntity(UUID visit,UUID patient,int number,UUID doctor,Instant now){id=UUID.randomUUID();visitId=visit;patientId=patient;roundNumber=number;orderedByDoctorId=doctor;orderedAt=now;createdAt=now;}
 public UUID getId(){return id;} public UUID getVisitId(){return visitId;} public UUID getPatientId(){return patientId;} public int getRoundNumber(){return roundNumber;} public UUID getOrderedByDoctorId(){return orderedByDoctorId;} public Instant getOrderedAt(){return orderedAt;}
}
