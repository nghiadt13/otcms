package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import fu.fa26.sep490.g58.otcms_be.clinical.model.*;
import fu.fa26.sep490.g58.otcms_be.clinical.service.*;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** IT-BUG-30: the delete of the old PRIMARY diagnosis must reach the database before the new PRIMARY is inserted. */
@SpringBootTest
@LocalPostgresTest
class ClinicalExaminationRepeatedSavePostgresIntegrationTest {
    @MockitoBean VisitClinicalContextPort visits;

    @Autowired ClinicalExaminationService examinations;
    @Autowired JdbcTemplate jdbc;

    private final UUID visitId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();

    @AfterEach
    void removeExamination() {
        jdbc.update("delete from examinations where visit_id = ?", visitId);
    }

    @Test
    void completingAfterDraftReplacesThePrimaryDiagnosisInsteadOfViolatingTheUniqueIndex() {
        when(visits.getVisitContext(visitId)).thenReturn(new VisitClinicalContextPort.ClinicalVisitContext(
                visitId, UUID.randomUUID(), doctorId, true));

        examinations.saveDraft(command("M25.5"));
        var completed = examinations.completeExamination(command("S83.5"));

        assertEquals(ClinicalExaminationStatus.COMPLETED, completed.status());
        assertEquals(List.of("S83.5"), jdbc.queryForList("select d.code from examination_diagnoses d "
                + "join examinations e on e.id = d.examination_id where e.visit_id = ? and d.type = 'PRIMARY'",
                String.class, visitId));
    }

    private ClinicalExaminationService.ExaminationCommand command(String primaryCode) {
        return new ClinicalExaminationService.ExaminationCommand(visitId, doctorId, "Reason", "Symptoms", "Findings",
                "Knee", "LEFT", "Twist", List.of(new ClinicalExaminationService.Diagnosis(primaryCode, DiagnosisType.PRIMARY)),
                "Pain", List.of("HOME_CARE"));
    }
}
