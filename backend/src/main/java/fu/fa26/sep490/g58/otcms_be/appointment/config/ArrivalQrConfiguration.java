package fu.fa26.sep490.g58.otcms_be.appointment.config;

import fu.fa26.sep490.g58.otcms_be.config.ArrivalQrProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ArrivalQrProperties.class)
public class ArrivalQrConfiguration { }
