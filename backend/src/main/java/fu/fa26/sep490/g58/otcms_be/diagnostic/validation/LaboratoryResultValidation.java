package fu.fa26.sep490.g58.otcms_be.diagnostic.validation;

import fu.fa26.sep490.g58.otcms_be.diagnostic.model.LaboratoryResultValue;
import java.util.List;
import java.util.regex.Pattern;

/** Validates transcribed values without inferring units or reference ranges. */
public final class LaboratoryResultValidation {
    private static final Pattern NON_NEGATIVE_DECIMAL = Pattern.compile("(?:\\d+(?:\\.\\d+)?|\\.\\d+)");

    private LaboratoryResultValidation() { }

    /** Incomplete drafts may be saved, but each row must have an indicator and a non-negative numeric result. */
    public static boolean validDraft(List<LaboratoryResultValue> values) {
        return values != null && !values.isEmpty() && values.stream().allMatch(value -> value != null
                && present(value.indicator()) && validResult(value.value()));
    }

    /** Missing unit or range requires clinical confirmation; release remains blocked. */
    public static boolean readyForRelease(List<LaboratoryResultValue> values) {
        return validDraft(values) && values.stream().allMatch(value -> present(value.unit())
                && present(value.referenceRange()) && value.abnormal() != null);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean validResult(String value) {
        return present(value) && NON_NEGATIVE_DECIMAL.matcher(value.trim()).matches();
    }
}
