package fu.fa26.sep490.g58.otcms_be.billing.model.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateDiagnosticInvoiceRequest(@NotEmpty @Size(max = 50) List<@NotNull UUID> orderIds) { }
