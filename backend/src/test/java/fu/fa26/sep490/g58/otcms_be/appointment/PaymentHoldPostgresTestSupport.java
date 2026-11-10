package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.billing.service.AppointmentHoldSettlementService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

/** Fixture with billing's settlement mocked (defaults to NO_INVOICE): isolates the appointment-side behaviour. */
abstract class PaymentHoldPostgresTestSupport extends PaymentHoldFixtureSupport {
    @MockitoBean AppointmentHoldSettlementService settlement;

    @BeforeEach
    void stubSettlementWithNoInvoice() {
        when(settlement.settle(any(UUID.class), anyBoolean())).thenReturn(AppointmentHoldSettlementService.Outcome.NO_INVOICE);
    }
}
