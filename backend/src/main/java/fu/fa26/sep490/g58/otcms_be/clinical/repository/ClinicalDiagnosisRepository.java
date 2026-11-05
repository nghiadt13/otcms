package fu.fa26.sep490.g58.otcms_be.clinical.repository;
import fu.fa26.sep490.g58.otcms_be.clinical.model.entity.ClinicalDiagnosisJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.List; import java.util.UUID;
public interface ClinicalDiagnosisRepository extends JpaRepository<ClinicalDiagnosisJpaEntity,UUID>{
    List<ClinicalDiagnosisJpaEntity> findByExaminationId(UUID examinationId); void deleteByExaminationId(UUID examinationId);
    List<ClinicalDiagnosisJpaEntity> findByExaminationIdIn(java.util.Collection<UUID> examinationIds);
}
