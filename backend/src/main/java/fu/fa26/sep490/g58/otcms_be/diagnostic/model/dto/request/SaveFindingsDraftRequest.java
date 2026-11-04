package fu.fa26.sep490.g58.otcms_be.diagnostic.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveFindingsDraftRequest(@NotBlank @Size(max = 20000) String content) { }
