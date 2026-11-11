package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.imaging.integration.orthanc.ArchiveInstanceReceipt;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingDicomStudyValidation;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyInstanceJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudySeriesJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingUidClaimId;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingUidClaimJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.validation.ValidatedDicomStudy;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyInstanceRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudySeriesRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingUidClaimRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists approved archive references and the minimum study/series/instance index in one transaction. */
@Service
public class ImagingStudyPersistenceService {
    private final ImagingStudyRepository studies;
    private final ImagingStudySeriesRepository series;
    private final ImagingStudyInstanceRepository instances;
    private final ImagingUidClaimRepository uidClaims;

    public ImagingStudyPersistenceService(ImagingStudyRepository studies, ImagingStudySeriesRepository series,
            ImagingStudyInstanceRepository instances, ImagingUidClaimRepository uidClaims) {
        this.studies = studies;
        this.series = series;
        this.instances = instances;
        this.uidClaims = uidClaims;
    }

    @Transactional
    public StudyReferences persistConfirmedStudy(ImagingOperationRecord operation, ImagingDicomStudyValidation validation,
            List<ValidatedDicomStudy.DicomObjectReference> objects, List<ArchiveInstanceReceipt> receipts) {
        if (objects.size() != receipts.size() || objects.isEmpty()) {
            throw new IllegalArgumentException("Archive receipt count does not match validated object count");
        }
        String archiveStudyRef = receipts.getFirst().archiveStudyRef();
        if (receipts.stream().anyMatch(receipt -> !archiveStudyRef.equals(receipt.archiveStudyRef()))) {
            throw new IllegalStateException("Orthanc returned multiple study references for one study");
        }
        var existing = studies.findByArchiveStudyRef(archiveStudyRef);
        if (existing.isPresent()) {
            ImagingStudyJpaEntity study = existing.get();
            if (!sameOwner(study, operation)
                    || !study.getStudyInstanceUid().equals(validation.studyInstanceUid())
                    || !study.getModality().equals(validation.modality())
                    || study.getSeriesCount() != validation.seriesCount()
                    || study.getInstanceCount() != validation.instanceCount()) {
                throw new ImagingOperationException(ImagingOperationOutcome.ARCHIVE_STUDY_ALREADY_LINKED);
            }
            return new StudyReferences(study.getStudyRef(), archiveStudyRef);
        }
        String studyRef = "img_" + UUID.randomUUID().toString().replace("-", "");
        studies.saveAndFlush(new ImagingStudyJpaEntity(studyRef, operation.id(), operation.requestId(),
                operation.attemptId(), operation.visitId(), operation.patientId(), archiveStudyRef,
                validation.studyInstanceUid(), validation.modality(), validation.seriesCount(),
                validation.instanceCount(), validation.verificationStatus().name()));

        Map<String, List<Integer>> seriesIndexes = new LinkedHashMap<>();
        for (int index = 0; index < objects.size(); index++) {
            seriesIndexes.computeIfAbsent(objects.get(index).seriesInstanceUid(), ignored -> new java.util.ArrayList<>())
                    .add(index);
        }
        Map<String, UUID> seriesIds = new LinkedHashMap<>();
        for (var entry : seriesIndexes.entrySet()) {
            UUID seriesId = UUID.randomUUID();
            seriesIds.put(entry.getKey(), seriesId);
            String archiveSeriesRef = receipts.get(entry.getValue().getFirst()).archiveSeriesRef();
            for (int index : entry.getValue()) {
                if (!archiveSeriesRef.equals(receipts.get(index).archiveSeriesRef())) {
                    throw new IllegalStateException("Orthanc returned inconsistent series references");
                }
            }
            series.save(new ImagingStudySeriesJpaEntity(seriesId, studyRef, entry.getKey(), archiveSeriesRef,
                    entry.getValue().size()));
        }
        for (int index = 0; index < objects.size(); index++) {
            var object = objects.get(index);
            instances.save(new ImagingStudyInstanceJpaEntity(UUID.randomUUID(), seriesIds.get(object.seriesInstanceUid()),
                    object.sopInstanceUid(), receipts.get(index).archiveInstanceRef(), null));
        }
        uidClaims.save(new ImagingUidClaimJpaEntity(new ImagingUidClaimId("STUDY", validation.studyInstanceUid()), studyRef));
        for (String uid : seriesIds.keySet()) {
            uidClaims.save(new ImagingUidClaimJpaEntity(new ImagingUidClaimId("SERIES", uid), studyRef));
        }
        for (var object : objects) {
            uidClaims.save(new ImagingUidClaimJpaEntity(new ImagingUidClaimId("SOP", object.sopInstanceUid()), studyRef));
        }
        uidClaims.flush();
        return new StudyReferences(studyRef, archiveStudyRef);
    }

    private boolean sameOwner(ImagingStudyJpaEntity study, ImagingOperationRecord operation) {
        return study.getRequestId().equals(operation.requestId())
                && study.getAttemptId().equals(operation.attemptId())
                && study.getVisitId().equals(operation.visitId())
                && study.getPatientId().equals(operation.patientId());
    }

    public record StudyReferences(String studyRef, String archiveStudyRef) { }
}
