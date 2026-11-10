package fu.fa26.sep490.g58.otcms_be.common.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fu.fa26.sep490.g58.otcms_be.config.ProductionReadinessProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Releasing a payment hold frees capacity, so a closed readiness gate must not block it. */
class ProductionReadinessGatePendingHoldTest {
    private final ProductionReadinessGate gate = openGate();

    private static ProductionReadinessGate openGate() {
        var properties = new ProductionReadinessProperties();
        properties.setRequired(false);
        return ProductionReadinessGate.load(properties);
    }

    @Test
    void releasingAPaymentHoldIsNeverGated() {
        assertFalse(gate.protects("POST", "/api/v1/appointments/pending-hold/cancel"));
    }

    @Test
    void otherAppointmentCommandsStayGated() {
        assertTrue(gate.protects("POST", "/api/v1/appointments"));
        assertTrue(gate.protects("POST", "/api/v1/appointments/" + UUID.randomUUID() + "/cancel"));
        assertTrue(gate.protects("POST", "/api/v1/appointments/" + UUID.randomUUID() + "/check-in"));
    }
}
