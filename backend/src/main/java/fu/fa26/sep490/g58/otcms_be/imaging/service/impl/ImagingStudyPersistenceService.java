package fu.fa26.sep490.g58.otcms_be.imaging.service.impl;

import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationException;
import fu.fa26.sep490.g58.otcms_be.imaging.exception.ImagingOperationOutcome;
import fu.fa26.sep490.g58.otcms_be.imaging.integration.orthanc.ArchiveInstanceReceipt;
import fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingOperationRecord;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.validation.ValidatedDicomStudy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores an uploaded study and its DICOM index on one imaging_studies row. */
@Service
public class ImagingStudyPersistenceService {
    private final ImagingStudyRepository studies;

    public ImagingStudyPersistenceService(ImagingStudyRepository studies) {
        this.studies = studies;
    }

    @Transactional
    public StudyReferences persistConfirmedStudy(ImagingOperationRecord operation,
            fu.fa26.sep490.g58.otcms_be.imaging.model.ImagingDicomStudyValidation validation,
            List<ValidatedDicomStudy.DicomObjectReference> objects, List<ArchiveInstanceReceipt> receipts) {
        if (objects.size() != receipts.size() || objects.isEmpty()) {
            throw new IllegalArgumentException("Archive receipt count does not match validated object count");
        }
        String archiveStudyRef = receipts.getFirst().archiveStudyRef();
        if (receipts.stream().anyMatch(receipt -> !archiveStudyRef.equals(receipt.archiveStudyRef()))) {
            throw new IllegalStateException("Orthanc returned multiple study references for one study");
        }
        String studyRef = "img_" + UUID.randomUUID().toString().replace("-", "");
        Map<String, List<Integer>> seriesIndexes = new LinkedHashMap<>();
        for (int index = 0; index < objects.size(); index++) {
            seriesIndexes.computeIfAbsent(objects.get(index).seriesInstanceUid(), ignored -> new ArrayList<>())
                    .add(index);
        }
        List<ImagingStudyJpaEntity.DicomSeries> indexedSeries = new ArrayList<>();
        for (var entry : seriesIndexes.entrySet()) {
            String archiveSeriesRef = receipts.get(entry.getValue().getFirst()).archiveSeriesRef();
            if (entry.getValue().stream().anyMatch(index ->
                    !archiveSeriesRef.equals(receipts.get(index).archiveSeriesRef()))) {
                throw new IllegalStateException("Orthanc returned inconsistent series references");
            }
            List<ImagingStudyJpaEntity.DicomInstance> indexedInstances = entry.getValue().stream()
                    .map(index -> new ImagingStudyJpaEntity.DicomInstance(objects.get(index).sopInstanceUid(),
                            receipts.get(index).archiveInstanceRef(), null))
                    .toList();
            indexedSeries.add(new ImagingStudyJpaEntity.DicomSeries(entry.getKey(), archiveSeriesRef,
                    indexedInstances));
        }
        ImagingStudyJpaEntity study = studies.findById(operation.id())
                .orElseThrow(() -> new ImagingOperationException(ImagingOperationOutcome.RECORD_NOT_FOUND));
        study.applyValidation(validation);
        study.persistConfirmedStudy(studyRef, archiveStudyRef,
                new ImagingStudyJpaEntity.DicomIndex(List.copyOf(indexedSeries)), Instant.now());
        studies.saveAndFlush(study);
        return new StudyReferences(studyRef, archiveStudyRef);
    }

    public record StudyReferences(String studyRef, String archiveStudyRef) { }
}
