package fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** {@code reportId} is optional (UC-50 AF2): null completes the findings without a PDF report. */
public record CompleteFindingsRequest(@Min(1) int findingsVersion, UUID reportId,
        @NotBlank @Size(max = 255) String studyRef) { }
