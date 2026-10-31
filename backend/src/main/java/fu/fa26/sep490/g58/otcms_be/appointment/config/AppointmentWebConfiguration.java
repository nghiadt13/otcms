package fu.fa26.sep490.g58.otcms_be.appointment.config;

import fu.fa26.sep490.g58.otcms_be.appointment.web.PublicRequestRateLimitInterceptor;
import fu.fa26.sep490.g58.otcms_be.common.web.ProductionReadinessGateInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AppointmentWebConfiguration implements WebMvcConfigurer {
    private final PublicRequestRateLimitInterceptor rateLimitInterceptor;
    private final ProductionReadinessGateInterceptor readinessGateInterceptor;

    public AppointmentWebConfiguration(PublicRequestRateLimitInterceptor rateLimitInterceptor,
            ProductionReadinessGateInterceptor readinessGateInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.readinessGateInterceptor = readinessGateInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor);
        registry.addInterceptor(readinessGateInterceptor);
    }
}
