package fu.fa26.sep490.g58.otcms_be.clinical;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import fu.fa26.sep490.g58.otcms_be.clinical.controller.ClinicalImagingRequestController;
import fu.fa26.sep490.g58.otcms_be.clinical.security.ClinicalDoctorIdentityResolver;
import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalImagingRequestService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.controller.DiagnosticRequestController;
import fu.fa26.sep490.g58.otcms_be.diagnostic.mapper.DiagnosticDtoMapper;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticRequestService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class DoctorCoreMvcMappingTest {
    @Test void registersExactlyOnePublicUc38HandlerWithBothProductionControllers() {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(MvcConfiguration.class);
            context.refresh();
            var mapping = context.getBean(org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class);
            var handlers = mapping.getHandlerMethods().entrySet().stream().filter(entry ->
                    entry.getKey().getPatternValues().contains("/api/v1/visits/{visitId}/imaging-requests")
                    && entry.getKey().getMethodsCondition().getMethods().contains(org.springframework.web.bind.annotation.RequestMethod.POST)).toList();
            assertEquals(1, handlers.size());
            assertEquals(ClinicalImagingRequestController.class, handlers.getFirst().getValue().getBeanType());
            assertEquals("submit", handlers.getFirst().getValue().getMethod().getName());
        }
    }

    @Configuration @EnableWebMvc
    @Import({ClinicalImagingRequestController.class, DiagnosticRequestController.class, DiagnosticDtoMapper.class})
    static class MvcConfiguration {
        @Bean ClinicalImagingRequestService clinicalRequests() { return mock(ClinicalImagingRequestService.class); }
        @Bean ClinicalDoctorIdentityResolver doctors() { return mock(ClinicalDoctorIdentityResolver.class); }
        @Bean DiagnosticRequestService diagnosticRequests() { return mock(DiagnosticRequestService.class); }
        @Bean DiagnosticStatusService statuses() { return mock(DiagnosticStatusService.class); }
    }
}
