package fu.fa26.sep490.g58.otcms_be.imaging.repository;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingUidClaimId;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingUidClaimJpaEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagingUidClaimRepository extends JpaRepository<ImagingUidClaimJpaEntity, ImagingUidClaimId> {
    List<ImagingUidClaimJpaEntity> findAllByStudyRef(String studyRef);
}
