package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

import fu.fa26.sep490.g58.otcms_be.clinical.integration.visit.VisitClinicalContextAdapter;
import fu.fa26.sep490.g58.otcms_be.clinical.service.VisitClinicalContextPort;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.ClinicVisitJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitCareAssignmentJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.ClinicVisitJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitCareAssignmentRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitClinicalCareContextService;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitExamQueueService;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.VisitClinicalCareContextServiceImpl;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class VisitClinicalContextProductionWiringTest {
    @Test void springDiscoversRealAdapterAndWiresRealVisitQueryWithOnlyPersistenceMocked() {
        var visits = mock(ClinicVisitJpaRepository.class);
        var assignments = mock(VisitCareAssignmentRepository.class);
        UUID visitId = UUID.randomUUID(), patientId = UUID.randomUUID(), doctorId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-04T10:00:00Z");
        when(visits.findById(visitId)).thenReturn(Optional.of(
                ClinicVisitJpaEntity.walkIn(visitId, patientId, null, UUID.randomUUID(), now)));
        when(assignments.findFirstByVisitIdAndStatusOrderByAssignedAtDescIdDesc(visitId, "IN_PROGRESS"))
                .thenReturn(Optional.of(VisitCareAssignmentJpaEntity.inProgress(UUID.randomUUID(), visitId,
                        doctorId, "EXAMINATION", 30, now, now.plusSeconds(1800))));
        new ApplicationContextRunner().withUserConfiguration(ProductionBeans.class)
                .withBean(ClinicVisitJpaRepository.class, () -> visits)
                .withBean(VisitCareAssignmentRepository.class, () -> assignments)
                // The package scan also finds VisitConclusionAdapter, which needs the exam queue; it is not under test here.
                .withBean(VisitExamQueueService.class, () -> mock(VisitExamQueueService.class))
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(VisitClinicalContextPort.class)
                            .hasSingleBean(VisitClinicalCareContextService.class);
                    assertThat(context.getBean(VisitClinicalContextPort.class)).isInstanceOf(VisitClinicalContextAdapter.class);
                    var facts = context.getBean(VisitClinicalContextPort.class).getVisitContext(visitId);
                    assertThat(facts.patientId()).isEqualTo(patientId);
                    assertThat(facts.responsibleDoctorId()).isEqualTo(doctorId);
                    assertThat(facts.clinicalWorkAllowed()).isTrue();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = VisitClinicalContextAdapter.class)
    @Import(VisitClinicalCareContextServiceImpl.class)
    static class ProductionBeans { }
}
