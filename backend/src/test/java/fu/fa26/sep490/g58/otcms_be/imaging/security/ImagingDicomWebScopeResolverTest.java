package fu.fa26.sep490.g58.otcms_be.imaging.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyInstanceJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudySeriesJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyInstanceRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudySeriesRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImagingDicomWebScopeResolverTest {
    private static final String STUDY_UID = "1.2.840.10008.1";
    private static final String SERIES_UID = "1.2.840.10008.1.1";
    private static final String SOP_UID = "1.2.840.10008.1.1.1";
    private static final String STUDY_REF = "opaque-study-ref";

    @Mock ImagingStudyRepository studies;
    @Mock ImagingStudySeriesRepository series;
    @Mock ImagingStudyInstanceRepository instances;

    @Test
    void allowsOnlyStudyScopedQidoAndResolvesOpaqueStudyReference() {
        when(studies.findByStudyInstanceUid(STUDY_UID)).thenReturn(Optional.of(study()));

        var result = resolver().resolve("/studies", Map.of("StudyInstanceUID", new String[] {STUDY_UID}));

        assertTrue(result.isPresent());
        assertEquals(STUDY_REF, result.orElseThrow().studyRef());
        assertEquals(STUDY_UID, result.orElseThrow().studyInstanceUid());
    }

    @Test
    void rejectsUnscopedOrAmbiguousStudyQueriesAndPathTraversal() {
        assertTrue(resolver().resolve("/studies", Map.of()).isEmpty());
        assertTrue(resolver().resolve("/studies", Map.of("StudyInstanceUID",
                new String[] {STUDY_UID, "1.2.3"})).isEmpty());
        assertTrue(resolver().resolve("/studies/../instances", Map.of()).isEmpty());
        assertTrue(resolver().resolve("/studies/" + STUDY_UID + "/series/" + SERIES_UID,
                Map.of("StudyInstanceUID", new String[] {"1.2.3"})).isEmpty());
    }

    @Test
    void verifiesNestedSeriesAndSopInstanceBelongToTheAuthorizedStudy() {
        UUID seriesId = UUID.randomUUID();
        when(studies.findByStudyInstanceUid(STUDY_UID)).thenReturn(Optional.of(study()));
        when(series.findByStudyRefAndSeriesInstanceUid(STUDY_REF, SERIES_UID))
                .thenReturn(Optional.of(new ImagingStudySeriesJpaEntity(seriesId, STUDY_REF, SERIES_UID,
                        "archive-series", 1)));
        when(instances.findBySeriesIdAndSopInstanceUid(seriesId, SOP_UID))
                .thenReturn(Optional.of(new ImagingStudyInstanceJpaEntity(UUID.randomUUID(), seriesId, SOP_UID,
                        "archive-instance", 1)));

        var allowed = resolver().resolve("/studies/" + STUDY_UID + "/series/" + SERIES_UID
                + "/instances/" + SOP_UID + "/frames/1", Map.of());
        var wrongSop = resolver().resolve("/studies/" + STUDY_UID + "/series/" + SERIES_UID
                + "/instances/1.2.3.4", Map.of());

        assertTrue(allowed.isPresent());
        assertTrue(wrongSop.isEmpty());
    }

    @Test
    void scopesMetadataFrameBulkAndThumbnailRoutesToOnePersistedStudy() {
        UUID seriesId = UUID.randomUUID();
        when(studies.findByStudyInstanceUid(STUDY_UID)).thenReturn(Optional.of(study()));
        when(series.findByStudyRefAndSeriesInstanceUid(STUDY_REF, SERIES_UID))
                .thenReturn(Optional.of(new ImagingStudySeriesJpaEntity(seriesId, STUDY_REF, SERIES_UID,
                        "archive-series", 1)));
        when(instances.findBySeriesIdAndSopInstanceUid(seriesId, SOP_UID))
                .thenReturn(Optional.of(new ImagingStudyInstanceJpaEntity(UUID.randomUUID(), seriesId, SOP_UID,
                        "archive-instance", 1)));
        var resolver = resolver();
        String studyPath = "/studies/" + STUDY_UID;
        String instancePath = studyPath + "/series/" + SERIES_UID + "/instances/" + SOP_UID;

        for (String path : new String[] {studyPath + "/metadata", studyPath + "/thumbnail",
                studyPath + "/rendered", studyPath + "/series/" + SERIES_UID,
                studyPath + "/series/" + SERIES_UID + "/metadata", instancePath,
                instancePath + "/metadata", instancePath + "/rendered", instancePath + "/thumbnail",
                instancePath + "/frames/1,2", instancePath + "/bulkdata/7fe00010"}) {
            assertTrue(resolver.resolve(path, Map.of()).isPresent(), path);
        }

        assertTrue(resolver.resolve(instancePath + "/frames/0", Map.of()).isEmpty());
        assertTrue(resolver.resolve(instancePath + "/bulkdata/../other", Map.of()).isEmpty());
        assertTrue(resolver.resolve(instancePath, Map.of("SOPInstanceUID", new String[] {"1.2.3.4"})).isEmpty());
        assertTrue(resolver.resolve(studyPath + "/thumbnail", Map.of("StudyInstanceUID",
                new String[] {"1.2.840.10008.2"})).isEmpty());
    }

    private ImagingDicomWebScopeResolver resolver() {
        return new ImagingDicomWebScopeResolver(studies, series, instances);
    }

    private static ImagingStudyJpaEntity study() {
        return new ImagingStudyJpaEntity(STUDY_REF, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "archive-study", STUDY_UID, "CT", 1, 1,
                "CONFIRMED_MATCH");
    }
}
