package fu.fa26.sep490.g58.otcms_be.patient.mapper;

import fu.fa26.sep490.g58.otcms_be.patient.model.CareRelationship;
import fu.fa26.sep490.g58.otcms_be.patient.model.entity.CareRelationshipJpaEntity;

public final class CareRelationshipMapper {
    private CareRelationshipMapper() {
    }

    public static CareRelationship toModel(CareRelationshipJpaEntity entity) {
        return new CareRelationship(entity.getId(), entity.getRequesterAccountId(),
                entity.getDependentPatientProfileId(), entity.getConfirmingAccountId(), entity.getState(),
                entity.getRequestedAt(), entity.getConfirmedAt(), entity.getRevokedAt(),
                entity.getRevokedByAccountId());
    }

    public static CareRelationshipJpaEntity toEntity(CareRelationship relationship) {
        return CareRelationshipJpaEntity.from(relationship);
    }
}
