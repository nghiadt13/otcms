package fu.fa26.sep490.g58.otcms_be.diagnostic.validation;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.LaboratoryResultValue;
import java.util.List;

/** Validates transcribed values without inferring units or reference ranges. */
public final class LaboratoryResultValidation {
    private LaboratoryResultValidation() { }

    /** Incomplete drafts may be saved, but every row must identify its indicator and value. */
    public static boolean validDraft(List<LaboratoryResultValue> values) {
        return values != null && !values.isEmpty() && values.stream().allMatch(value -> value != null
                && present(value.indicator()) && present(value.value()));
    }

    /** Missing unit or range requires clinical confirmation; release remains blocked. */
    public static boolean readyForRelease(List<LaboratoryResultValue> values) {
        return validDraft(values) && values.stream().allMatch(value -> present(value.unit())
                && present(value.referenceRange()) && value.abnormal() != null);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
