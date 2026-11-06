package fu.fa26.sep490.g58.otcms_be.imaging.security;

import fu.fa26.sep490.g58.otcms_be.imaging.model.entity.ImagingStudyJpaEntity;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyInstanceRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudyRepository;
import fu.fa26.sep490.g58.otcms_be.imaging.repository.ImagingStudySeriesRepository;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Resolves and validates every DICOMweb resource against one persisted study before proxying. */
@Service
public class ImagingDicomWebScopeResolver {
    private static final Pattern UID = Pattern.compile("[0-9]+(?:\\.[0-9]+){1,63}");
    private static final Pattern FRAME_LIST = Pattern.compile("[1-9][0-9]*(?:,[1-9][0-9]*)*");
    private static final Pattern BULK_DATA_SEGMENT = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    private final ImagingStudyRepository studies;
    private final ImagingStudySeriesRepository series;
    private final ImagingStudyInstanceRepository instances;

    public ImagingDicomWebScopeResolver(ImagingStudyRepository studies,
            ImagingStudySeriesRepository series, ImagingStudyInstanceRepository instances) {
        this.studies = studies;
        this.series = series;
        this.instances = instances;
    }

    public Optional<StudyScope> resolve(String rawPath, Map<String, String[]> query) {
        if (!safePath(rawPath) || query == null) return Optional.empty();
        String[] segments = rawPath.split("/", -1);
        if (segments.length < 2 || !"studies".equals(segments[1])) return Optional.empty();
        String pathStudyUid = null;
        int cursor;
        if (segments.length == 2) {
            cursor = 2;
        } else {
            pathStudyUid = segments[2];
            if (!isUid(pathStudyUid)) return Optional.empty();
            cursor = 3;
        }
        String queryStudyUid = singleUid(query, "StudyInstanceUID");
        if (hasInvalidUidFilter(query, "StudyInstanceUID")) return Optional.empty();
        if (pathStudyUid != null && queryStudyUid != null && !pathStudyUid.equals(queryStudyUid)) {
            return Optional.empty();
        }
        String studyUid = pathStudyUid == null ? queryStudyUid : pathStudyUid;
        if (studyUid == null) return Optional.empty();
        ImagingStudyJpaEntity study = studies.findByStudyInstanceUid(studyUid).orElse(null);
        if (study == null) return Optional.empty();

        String pathSeriesUid = null;
        String pathSopUid = null;
        if (cursor < segments.length && !segments[cursor].isEmpty()) {
            String root = segments[cursor++];
            if ("metadata".equals(root) || "series".equals(root) || "instances".equals(root)
                    || "thumbnail".equals(root) || "rendered".equals(root)) {
                if (!validStudyRoute(root, segments, cursor)) return Optional.empty();
            } else {
                return Optional.empty();
            }
            if ("series".equals(root) && cursor < segments.length) {
                pathSeriesUid = segments[cursor++];
                if (!isUid(pathSeriesUid) || series.findByStudyRefAndSeriesInstanceUid(
                        study.getStudyRef(), pathSeriesUid).isEmpty()) return Optional.empty();
                if (cursor < segments.length) {
                    if (!"metadata".equals(segments[cursor]) && !"instances".equals(segments[cursor])) {
                        return Optional.empty();
                    }
                    cursor++;
                    if (cursor < segments.length && "instances".equals(segments[cursor - 1])) {
                        pathSopUid = cursor < segments.length ? segments[cursor++] : null;
                        if (!isUid(pathSopUid)) return Optional.empty();
                        var parentSeries = series.findByStudyRefAndSeriesInstanceUid(study.getStudyRef(), pathSeriesUid)
                                .orElseThrow();
                        if (instances.findBySeriesIdAndSopInstanceUid(parentSeries.getId(), pathSopUid).isEmpty()) {
                            return Optional.empty();
                        }
                        if (!validInstanceSuffix(segments, cursor)) return Optional.empty();
                        cursor = segments.length;
                    }
                }
            } else if ("instances".equals(root)) {
                // QIDO collection is permitted; unscoped instance resource paths are not.
                if (cursor < segments.length) return Optional.empty();
            } else if (cursor < segments.length) {
                return Optional.empty();
            }
        }
        if (cursor != segments.length) return Optional.empty();
        if (!matchesOptionalUidFilter(query, "SeriesInstanceUID", pathSeriesUid)
                || !matchesOptionalUidFilter(query, "SOPInstanceUID", pathSopUid)) return Optional.empty();
        return Optional.of(new StudyScope(study.getStudyRef(), studyUid));
    }

    private static boolean validStudyRoute(String root, String[] segments, int cursor) {
        if ("series".equals(root)) return true;
        if ("metadata".equals(root) || "thumbnail".equals(root) || "rendered".equals(root)) {
            return cursor == segments.length;
        }
        return "instances".equals(root) && cursor == segments.length;
    }

    private static boolean validInstanceSuffix(String[] segments, int cursor) {
        if (cursor == segments.length) return true;
        String suffix = segments[cursor++];
        if ("metadata".equals(suffix) || "rendered".equals(suffix) || "thumbnail".equals(suffix)) {
            return cursor == segments.length;
        }
        if ("frames".equals(suffix)) {
            return cursor < segments.length && cursor + 1 == segments.length
                    && FRAME_LIST.matcher(segments[cursor]).matches();
        }
        if ("bulkdata".equals(suffix)) {
            return cursor < segments.length && Arrays.stream(segments, cursor, segments.length)
                    .allMatch(segment -> BULK_DATA_SEGMENT.matcher(segment).matches());
        }
        return false;
    }

    private static boolean matchesOptionalUidFilter(Map<String, String[]> query, String name, String pathUid) {
        String filter = singleUid(query, name);
        if (hasInvalidUidFilter(query, name)) return false;
        return filter == null || pathUid == null || pathUid.equals(filter);
    }

    private static String singleUid(Map<String, String[]> query, String name) {
        String[] values = query.get(name);
        if (values == null) return null;
        return values.length == 1 && isUid(values[0]) ? values[0] : null;
    }

    private static boolean hasInvalidUidFilter(Map<String, String[]> query, String name) {
        String[] values = query.get(name);
        return values != null && (values.length != 1 || !isUid(values[0]));
    }

    private static boolean isUid(String value) {
        return value != null && UID.matcher(value).matches();
    }

    private static boolean safePath(String path) {
        if (path == null || path.isBlank() || path.length() > 2048 || !path.startsWith("/")) return false;
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        return !path.contains("..") && !path.contains("\\") && !path.contains("//")
                && !lower.contains("%2f") && !lower.contains("%5c") && !lower.contains("%00")
                && Arrays.stream(path.split("/", -1)).skip(1).noneMatch(String::isEmpty);
    }

    public record StudyScope(String studyRef, String studyInstanceUid) { }
}
