package fu.fa26.sep490.g58.otcms_be.common.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** Safe operational status; contains no evidence file contents, key material or clinical data. */
@Schema(name = "ProductionReadinessStatus", requiredProperties = {"commandEndpointsEnabled"}, additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class ProductionReadinessStatusResponse {
    private final boolean commandEndpointsEnabled;

    public ProductionReadinessStatusResponse(boolean commandEndpointsEnabled) {
        this.commandEndpointsEnabled = commandEndpointsEnabled;
    }

    public boolean isCommandEndpointsEnabled() { return commandEndpointsEnabled; }
    public boolean getCommandEndpointsEnabled() { return commandEndpointsEnabled; }
}
