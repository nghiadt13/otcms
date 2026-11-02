package fu.fa26.sep490.g58.otcms_be.common.api;

import fu.fa26.sep490.g58.otcms_be.common.web.ProductionReadinessGate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductionReadinessStatusController {
    private final ProductionReadinessGate gate;

    public ProductionReadinessStatusController(ProductionReadinessGate gate) {
        this.gate = gate;
    }

    @GetMapping("/api/v1/system/production-readiness")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ProductionReadinessStatusResponse status() {
        return new ProductionReadinessStatusResponse(!gate.isClosed());
    }
}
