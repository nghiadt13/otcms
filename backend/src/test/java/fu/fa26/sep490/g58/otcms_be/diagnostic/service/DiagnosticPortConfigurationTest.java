package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.ImagingServiceCatalogPort;
import fu.fa26.sep490.g58.otcms_be.diagnostic.integration.DiagnosticPortModels.ImagingService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.integration.diagnostic.ImagingServiceCatalogAdapter;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Loads only port wiring; no Spring Boot/JPA/datasource context. */
class DiagnosticPortConfigurationTest {
    @Test void sharedLifecycleFixtureSelectsItsFakeCatalogWithoutRemovingProductionAdapter() {
        var catalog = mock(GetClinicServiceCatalogService.class);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(DiagnosticRequestT012PostgresIntegrationTest.PortConfiguration.class);
            context.registerBean("imagingServiceCatalogAdapter", ImagingServiceCatalogAdapter.class,
                    () -> new ImagingServiceCatalogAdapter(catalog));
            context.refresh();
            assertEquals(2, context.getBeansOfType(ImagingServiceCatalogPort.class).size());
            assertInstanceOf(ImagingServiceCatalogAdapter.class, context.getBean("imagingServiceCatalogAdapter"));
            var selected = context.getBean(ImagingServiceCatalogPort.class);
            assertSame(context.getBean("imagingServiceCatalogPort"), selected);
            var source = new ImagingService(UUID.randomUUID(), "MRI", true);
            context.getBean(DiagnosticRequestT012PostgresIntegrationTest.PortFakes.class).service = source;
            assertSame(source, selected.requireImagingService(source.serviceId(), "MRI"));
            verifyNoInteractions(catalog);
            assertTrue(context.getBeansOfType(javax.sql.DataSource.class).isEmpty());
        }
    }
}
