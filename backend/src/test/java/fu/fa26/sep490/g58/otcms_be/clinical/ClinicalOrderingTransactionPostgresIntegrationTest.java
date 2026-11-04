package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.clinical.model.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.*;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@LocalPostgresTest
class ClinicalOrderingTransactionPostgresIntegrationTest {
    @MockitoBean VisitClinicalContextPort visits;
    @MockitoBean ClinicServiceCatalogPort catalog;

    @Autowired ClinicalOrderingService ordering;
    @Autowired JdbcTemplate jdbc;

    @Test
    void databaseFailureOnLaterOrderRollsBackRoundAndEarlierOrder() {
        UUID visitId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID doctorId = UUID.randomUUID();
        UUID labId = UUID.randomUUID();
        UUID imagingId = UUID.randomUUID();
        when(visits.getVisitContext(visitId)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(
                visitId, patientId, doctorId, ClinicalVisitStatus.IN_EXAMINATION));
        when(catalog.getService(labId)).thenReturn(new ClinicServiceCatalogPort.ClinicalServiceSnapshot(
                labId, "CBC", ClinicalServiceType.LABORATORY, new BigDecimal("450.00"), true));
        when(catalog.getService(imagingId)).thenReturn(new ClinicServiceCatalogPort.ClinicalServiceSnapshot(
                imagingId, "X-Ray", ClinicalServiceType.IMAGING, new BigDecimal("1250.00"), true));

        var command = new ClinicalOrderingService.SubmitRound(visitId, doctorId, List.of(
                new ClinicalOrderingService.OrderRequest(labId, "Reason", null, null),
                new ClinicalOrderingService.OrderRequest(imagingId, "Reason", "X".repeat(256), null)));

        assertThrows(RuntimeException.class, () -> ordering.submit(command));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from clinical_ordering_rounds where visit_id = ?", Integer.class, visitId));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from clinical_service_orders where visit_id = ?", Integer.class, visitId));
    }
}
